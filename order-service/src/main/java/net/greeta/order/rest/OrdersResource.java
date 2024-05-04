package net.greeta.order.rest;

import jakarta.validation.Valid;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.greeta.order.domain.port.OrderUseCasePort;
import net.greeta.order.models.*;
import net.greeta.order.streams.OrdersQueries;
import net.greeta.order.common.domain.dto.order.Order;
import net.greeta.order.common.domain.dto.order.OrderRequest;
import org.apache.pinot.client.Connection;
import org.apache.pinot.client.ConnectionFactory;
import org.apache.pinot.client.ResultSet;
import org.apache.pinot.client.ResultSetGroup;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jooq.impl.DSL.*;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrdersResource {

    private Connection connection = ConnectionFactory.fromHostList(System.getenv().getOrDefault("PINOT_BROKER",  "localhost:8099"));

    private final OrderUseCasePort orderUseCase;

    private final OrdersQueries ordersQueries;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UUID placeOrder(@RequestBody @Valid OrderRequest orderRequest) {
        log.info("Received new order request {}", orderRequest);
        return orderUseCase.placeOrder(orderRequest);
    }

    @GetMapping("{orderId}")
    public Order getOrder(@PathVariable UUID orderId) {
        return orderUseCase.getOrder(orderId);
    }

//    @GET
//    @Path("/overview")
//    public Response overview() {
//        OrdersSummary ordersSummary = ordersQueries.ordersSummary();
//        return Response.ok(ordersSummary).build();
//    }

    @GetMapping("/overview")
    public ResponseEntity overview() {
        OrdersSummary ordersSummary = ordersQueries.ordersSummary();
        return ResponseEntity.ok(ordersSummary);
    }

    @GetMapping("/overview2")
    public ResponseEntity overview2() {
        ResultSet resultSet = runQuery(connection, "select count(*) from orders limit 10");

        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        count()
                                .filterWhere("ts > ago('PT1M')")
                                .as("events1Min"),

                        count()
                                .filterWhere("ts <= ago('PT1M') AND ts > ago('PT2M')")
                                .as("events1Min2Min"),

                        sum(field("price").coerce(Long.class))
                                .filterWhere("ts > ago('PT1M')")
                                .as("total1Min"),

                        sum(field("price").coerce(Long.class))
                                .filterWhere("ts <= ago('PT1M') AND ts > ago('PT2M')")
                                .as("total1Min2Min")

                ).from("orders").getSQL();

        ResultSet summaryResults = runQuery(connection, query);

        TimePeriod currentTimePeriod = new TimePeriod(
                summaryResults.getLong(0, 0), summaryResults.getDouble(0, 2));
        TimePeriod previousTimePeriod = new TimePeriod(
                summaryResults.getLong(0, 1), summaryResults.getDouble(0, 3));
        OrdersSummary ordersSummary = new OrdersSummary(
                currentTimePeriod, previousTimePeriod
        );

        return ResponseEntity.ok(ordersSummary);
    }

    @GetMapping("/ordersPerMinute")
    public ResponseEntity ordersPerMinute() {
        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("ToDateTime(DATETRUNC('MINUTE', ts), 'yyyy-MM-dd HH:mm:ss')")
                                .as("dateMin"),
                        count(field("*")),
                        sum(field("price").coerce(Long.class))
                )
                .from("orders")
                .groupBy(field("dateMin"))
                .orderBy(field("dateMin"))
                .$where(field("dateMin").greaterThan(field("ago('PT1H')")))
                .$limit(DSL.inline(60))
                .getSQL();

        ResultSet summaryResults = runQuery(connection, query);

        int rowCount = summaryResults.getRowCount();

        List<SummaryRow> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            rows.add(new SummaryRow(
                    summaryResults.getString(index, 0),
                    summaryResults.getLong(index, 1),
                    summaryResults.getDouble(index, 2)
            ));
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/popular")
    public ResponseEntity popular() {
        String itemQuery = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("product.name").as("product"),
                        field("product.image").as("image"),
                        field("distinctcount(orderId)").as("orders"),
                        sum(field("orderItem.quantity").coerce(Long.class)).as("quantity")
                )
                .from("order_items_enriched")
                .where(field("ts").greaterThan(field("ago('PT1M')")))
                .groupBy(field("product"), field("image"))
                .orderBy(field("count(*)").desc())
                .limit(DSL.inline(5))
                .getSQL();

        ResultSet itemsResult = runQuery(connection, itemQuery);

        List<PopularItem> popularItems = new ArrayList<>();
        for (int index = 0; index < itemsResult.getRowCount(); index++) {
            popularItems.add(new PopularItem(
                    itemsResult.getString(index, 0),
                    itemsResult.getString(index, 1),
                    itemsResult.getLong(index, 2),
                    itemsResult.getDouble(index, 3)
            ));
        }

        String categoryQuery = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("product.category").as("category"),
                        field("distinctcount(orderId)").as("orders"),
                        sum(field("orderItem.quantity").coerce(Long.class)).as("quantity")
                )
                .from("order_items_enriched")
                .where(field("ts").greaterThan(field("ago('PT1M')")))
                .groupBy(field("category"))
                .orderBy(field("count(*)").desc())
                .limit(DSL.inline(5))
                .getSQL();

        ResultSet categoryResult = runQuery(connection, categoryQuery);

        List<PopularCategory> popularCategories = new ArrayList<>();
        for (int index = 0; index < categoryResult.getRowCount(); index++) {
            popularCategories.add(new PopularCategory(
                    categoryResult.getString(index, 0),
                    categoryResult.getLong(index, 1),
                    categoryResult.getDouble(index, 2)
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", popularItems);
        result.put("categories", popularCategories);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/latestOrders")
    public ResponseEntity latestOrders() {
        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("ToDateTime(ts, 'HH:mm:ss:SSS')")
                                .as("dateTime"),
                        field("price"),
                        field("userId"),
                        field("productsOrdered"),
                        field("totalQuantity")
                )
                .from("orders")
                .orderBy(field("ts").desc())
                .limit(DSL.inline(10))
                .getSQL();

        ResultSet summaryResults = runQuery(connection, query);

        int rowCount = summaryResults.getRowCount();

        List<OrderRow> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            rows.add(new OrderRow(
                    summaryResults.getString(index, 0),
                    summaryResults.getDouble(index, 1),
                    summaryResults.getLong(index, 2),
                    summaryResults.getLong(index, 3),
                    summaryResults.getLong(index, 4)
            ));
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/users/{userId}/orders")
    public ResponseEntity userOrders(@PathParam("userId") String userId) {
        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("id"),
                        field("price"),
                        field("ToDateTime(ts, 'YYYY-MM-dd HH:mm:ss')").as("ts")
                )
                .from("orders_enriched")
                .where(field("userId").eq(field("'" + userId + "'")))
                .orderBy(field("ts").desc())
                .limit(DSL.inline(50))
                .getSQL();

        ResultSet resultSet = runQuery(connection, query);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < resultSet.getRowCount(); index++) {
            rows.add(Map.of(
                    "id", resultSet.getString(index, 0),
                    "price", resultSet.getDouble(index, 1),
                    "ts", resultSet.getString(index, 2)
            ));
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/statuses")
    public ResponseEntity statuses() {
        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("status"),
                        min(field("(now() - ts) / 1000")),
                        field("percentile((now() - ts) / 1000, 50)"),
                        avg(field("(now() - ts) / 1000").coerce(Long.class)),
                        field("percentile((now() - ts) / 1000, 75)"),
                        field("percentile((now() - ts) / 1000, 90)"),
                        field("percentile((now() - ts) / 1000, 99)"),
                        max(field("(now() - ts) / 1000"))
                )
                .from("orders_enriched")
                .where(field("status NOT IN ('DELIVERED', 'OUT_FOR_DELIVERY')").coerce(Boolean.class))
                .groupBy(field("status"))
                .getSQL();

        ResultSet resultSet = runQuery(connection, query);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < resultSet.getRowCount(); index++) {
            rows.add(Map.of(
                    "status", resultSet.getString(index, 0),
                    "min", resultSet.getDouble(index, 1),
                    "percentile50", resultSet.getDouble(index, 2),
                    "avg", resultSet.getDouble(index, 3),
                    "percentile75", resultSet.getDouble(index, 4),
                    "percentile90", resultSet.getDouble(index, 5),
                    "percentile99", resultSet.getDouble(index, 6),
                    "max", resultSet.getDouble(index, 7)
            ));
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/stuck/{orderStatus}/{stuckTimeInMillis}")
    public ResponseEntity stuckOrders(
            @PathParam("orderStatus") String orderStatus,
            @PathParam("stuckTimeInMillis") Long stuckTimeInMillis
    ) {
        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("id"),
                        field("price"),
                        field("ts"),
                        field("(now() - ts) / 1000")
                )
                .from("orders_enriched")
                .where(field("status").eq(field("'" + orderStatus + "'")))
                .and(field("(now() - ts) > " + stuckTimeInMillis).coerce(Boolean.class))
                .orderBy(field("ts"))
                .getSQL();

        ResultSet resultSet = runQuery(connection, query);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < resultSet.getRowCount(); index++) {
            rows.add(Map.of(
                    "id", resultSet.getString(index, 0),
                    "price", resultSet.getDouble(index, 1),
                    "ts", resultSet.getString(index, 2),
                    "timeInStatus", resultSet.getDouble(index, 3)
            ));
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity order(@PathParam("orderId") String orderId) {
        String userQuery = DSL.using(SQLDialect.POSTGRES)
                .select(field("userId"))
                .select(field("deliveryLat"))
                .select(field("deliveryLon"))
                .from("orders")
                .where(field("id").eq(field("'" + orderId + "'")))
                .getSQL();
        ResultSet userResultSet = runQuery(connection, userQuery);
        Stream<Map<String, Object>> userInfo = IntStream.range(0,
                        userResultSet.getRowCount())
                .mapToObj(index -> Map.of(
                        "id", userResultSet.getString(index, 0),
                        "deliveryLat", userResultSet.getDouble(index, 1),
                        "deliveryLon", userResultSet.getDouble(index, 2)
                ));

        String productsQuery = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("product.name").as("product"),
                        field("product.price").as("price"),
                        field("product.image").as("image"),
                        field("orderItem.quantity").as("quantity")
                )
                .from("order_items_enriched")
                .where(field("orderId").eq(field("'" + orderId + "'")))
                .getSQL();
        ResultSet productsResultSet = runQuery(connection, productsQuery);
        Stream<Map<String, Object>> products = IntStream.range(0, productsResultSet.getRowCount())
                .mapToObj(index -> Map.of(
                        "product", productsResultSet.getString(index, 0),
                        "price", productsResultSet.getDouble(index, 1),
                        "image", productsResultSet.getString(index, 2),
                        "quantity", productsResultSet.getLong(index, 3)
                ));

        String statusesQuery = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("ToDateTime(ts, 'YYYY-MM-dd HH:mm:ss')").as("ts"),
                        field("status"),
                        field("userId").as("image")
                )
                .from("orders_enriched")
                .where(field("id").eq(field("'" + orderId + "'")))
                .orderBy(field("ts").desc())
                .option("option(skipUpsert=true)")
                .getSQL();
        ResultSet statusesResultSet = runQuery(connection, statusesQuery);
        Stream<Map<String, Object>> statuses = IntStream.range(0, statusesResultSet.getRowCount())
                .mapToObj(index -> Map.of(
                        "timestamp", statusesResultSet.getString(index, 0),
                        "status", statusesResultSet.getString(index, 1)
                ));

        String deliveryStatusQuery = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("ToDateTime(ts, 'YYYY-MM-dd HH:mm:ss')").as("ts"),
                        field("deliveryLat"),
                        field("deliveryLon")
                )
                .from("deliveryStatuses")
                .where(field("id").eq(field("'" + orderId + "'")))
                .getSQL();
        ResultSet deliveryStatusResultSet = runQuery(connection, deliveryStatusQuery);
        Stream<Map<String, Object>> deliveryStatus = IntStream.range(0,
                        deliveryStatusResultSet.getRowCount())
                .mapToObj(index -> Map.of(
                        "timestamp", deliveryStatusResultSet.getString(index, 0),
                        "lat", deliveryStatusResultSet.getDouble(index, 1),
                        "lon", deliveryStatusResultSet.getDouble(index, 2)
                ));

        Map<String, Object> response = new HashMap<>(Map.of(
                "user", userInfo,
                "products", products,
                "statuses", statuses
        ));

        deliveryStatus.findFirst().ifPresent(stringObjectMap ->
                response.put("deliveryStatus", stringObjectMap));

        return ResponseEntity.ok(response);
    }

    private static ResultSet runQuery(Connection connection, String query) {
        ResultSetGroup resultSetGroup = connection.execute(query);
        return resultSetGroup.getResultSet(0);
    }
}
