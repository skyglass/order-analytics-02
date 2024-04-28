package net.greeta.order.rest;

import jakarta.websocket.server.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.pinot.client.Connection;
import org.apache.pinot.client.ConnectionFactory;
import org.apache.pinot.client.ResultSet;
import org.apache.pinot.client.ResultSetGroup;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jooq.impl.DSL.field;

@Slf4j
@RestController
@RequestMapping("/users")
public class UsersResource {
    private Connection connection = ConnectionFactory.fromHostList(System.getenv().getOrDefault("PINOT_BROKER",  "localhost:8099"));


    @GetMapping("/{userId}/orders")
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

    @GetMapping("/")
    public ResponseEntity allUsers() {
        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("userId"),
                        field("ts")
                )
                .from("orders")
                .orderBy(field("ts").desc())
                .limit(DSL.inline(50))
                .getSQL();

        ResultSet resultSet = runQuery(connection, query);

        Stream<Map<String, Object>> rows = IntStream.range(0, resultSet.getRowCount())
                .mapToObj(index -> Map.of("userId", resultSet.getString(index, 0)));

        return ResponseEntity.ok(rows);
    }

    private static ResultSet runQuery(Connection connection, String query) {
        ResultSetGroup resultSetGroup = connection.execute(query);
        return resultSetGroup.getResultSet(0);
    }
}
