import random, time
from mysql.connector import connect, Error
from faker import Faker
from faker.providers import company
import json
from kafka import KafkaProducer
import datetime
import uuid
import math
import os
from dateutil import parser
from sortedcontainers import SortedList

from pyproj import Geod
import pyproj
import geopy.distance

import concurrent.futures

# CONFIG
usersLimit         = 1000
orderInterval      = 100
mysqlHost          = os.environ.get("MYSQL_SERVER", "localhost")
mysqlPort          = '3306'
mysqlUser          = 'mysqluser'
mysqlPass          = 'mysqlpw'
debeziumHostPort   = 'debezium:8083'
kafkaHostPort      = f"{os.environ.get('KAFKA_BROKER_HOSTNAME', 'localhost')}:{os.environ.get('KAFKA_BROKER_PORT', '29092')}"

print(f"Kafka broker: {kafkaHostPort}")

producer = KafkaProducer(bootstrap_servers=kafkaHostPort, api_version=(7, 1, 0), 
  value_serializer=lambda m: json.dumps(m).encode('utf-8'))


events_processed = 0
try:
    with connect(
        host=mysqlHost,
        user=mysqlUser,
        password=mysqlPass,
    ) as connection:
        with connection.cursor() as cursor:
            print("Getting products for the products topic")
            cursor.execute("SELECT id, name, description, category, price, image FROM pizzashop.products")
            products = [{
                "id": str(row[0]),
                "name": row[1],
                "description": row[2],
                "category": row[3],
                "price": row[4],
                "image": row[5]
                }
                for row in cursor
            ]

            for product in products:
                print(product["id"])
                producer.send('products', product, product["id"].encode("UTF-8"))
            producer.flush()

            cursor.execute("SELECT id,lat,lon FROM pizzashop.users")
            users = {row[0]: (row[1], row[2]) for row in cursor}
            user_ids = list(users.keys())

            print("Getting product ID and PRICE as tuples...")
            cursor.execute("SELECT id, price FROM pizzashop.products")
            product_prices = [(row[0], row[1]) for row in cursor]
            print(product_prices)

    connection.close()

except Error as e:
    print(e)

def create_new_order():
    number_of_items = random.randint(1,10)

    items = []
    for _ in range(0, number_of_items):
        product = random.choice(product_prices)        
        purchase_quantity = random.randint(1,5)
        items.append({
            "productId": str(product[0]),
            "quantity": purchase_quantity,
            "price": product[1]
        })

    user_id = random.choice(user_ids)
    prices = [item["quantity"] * item["price"] for item in items]
    total_price = round(math.fsum(prices), 2)

    return {
        "id": str(uuid.uuid4()),
        "createdAt": datetime.datetime.now().isoformat(),
        "userId": user_id,
        "price": total_price,
        "items": items,
        "deliveryLat": str(users[user_id][0]),
        "deliveryLon": str(users[user_id][1])
    }

STATUSES = [
    # PLACED_ORDER
    "ORDER_CONFIRMED",
    "BEING_PREPARED",
    "BEING_COOKED",
    "OUT_FOR_DELIVERY"
    # DELIVERED
]

WAIT_RANGES = {
    "ORDER_CONFIRMED": (60, 300),
    "BEING_PREPARED": (30, 120),
    "BEING_COOKED": (120, 180),
    "OUT_FOR_DELIVERY": (180,600)
}

other_statuses = SortedList(key=lambda x: x["updatedAt"])
delivery_statuses = SortedList(key=lambda x: x["updatedAt"])

# delivery_futures = []

shop_location = (12.978268132410502, 77.59408889388118)
driver_km_per_hour = 150
geoid = Geod(ellps="WGS84")

def generate_delivery_statuses(order, last_status_time, shop_location, delivery_location, points_to_generate):
    statuses = []
    if points_to_generate > 0:
        try:
            extra_points = geoid.npts(shop_location[0], shop_location[1], delivery_location[0], delivery_location[1], points_to_generate)
            for point in extra_points:    
                next_status_time = last_status_time + datetime.timedelta(seconds=1)

                statuses.append({
                    "id": order["id"],
                    "updatedAt": next_status_time.isoformat(),
                    "deliveryLat": str(point[0]),
                    "deliveryLon": str(point[1])
                })
                last_status_time = next_status_time
        except pyproj.exceptions.GeodError as e:
            print(e)

    statuses.append({
        "id": order["id"],
        "updatedAt": (last_status_time + datetime.timedelta(seconds=1)).isoformat(),
        "deliveryLat": str(delivery_location[0]),
        "deliveryLon": str(delivery_location[1])
    })
    return statuses

while True:
    order = create_new_order()

    order_status = {
        "id": order["id"],
        "updatedAt": order["createdAt"],
        "status": "PLACED_ORDER"
    }

    producer.send('orders', order, bytes(order["id"].encode("UTF-8")))
    producer.send('ordersStatuses', order_status, bytes(order_status["id"].encode("UTF-8")))

    # generate statuses for this order
    placed_order_time = parser.parse(order["createdAt"])
    
    # look into random.choices for future
    # random.choices(range(0,100), range(0,100), k=100)

    # process other statuses
    last_status_time = placed_order_time
    for index in range(0, len(STATUSES)):
        status = STATUSES[index]
        # min, max = WAIT_RANGES[status]
        min, max = tuple([item/10 for item in WAIT_RANGES[status]])
        next_status_time = last_status_time + datetime.timedelta(seconds=random.randint(min, max))

        other_statuses.add({
            "id": order["id"],
            "updatedAt": next_status_time.isoformat(),
            "status": status
        })
        last_status_time = next_status_time
    
    delivery_location = users[order["userId"]]
    dist = geopy.distance.distance(shop_location, delivery_location).meters
    minutes_to_deliver = (dist / (driver_km_per_hour * 1000)) * 60

    next_status_time = last_status_time + datetime.timedelta(seconds=minutes_to_deliver*60)
    other_statuses.add({
        "id": order["id"],
        "updatedAt": next_status_time.isoformat(),
        "status": "DELIVERED"
    })

    delivery_statuses.add({
        "id": order["id"],
        "updatedAt": last_status_time.isoformat(),
        "deliveryLat": str(shop_location[0]),
        "deliveryLon": str(shop_location[1])
    })

    points_to_generate = minutes_to_deliver * 60

    # with concurrent.futures.ProcessPoolExecutor() as executor:
    #     future = executor.submit(generate_delivery_statuses, order, last_status_time, shop_location, delivery_location, points_to_generate)
    #     future.add_done_callback(lambda ft: delivery_statuses.update(ft.result()))

        # delivery_futures.append(future)

    # completed_delivery_futures = [future for future in concurrent.futures.as_completed(delivery_futures) if future.done()]
    # print(f"Completed delivery statuses: {len(completed_delivery_futures)}")

    # for completed_future in completed_delivery_futures:
    #     delivery_statuses.update(completed_future.result())

    # points_to_generate = minutes_to_deliver * 60
    # if points_to_generate > 0:
    #     try:
    #         extra_points = geoid.npts(shop_location[0], shop_location[1], delivery_location[0], delivery_location[1], points_to_generate)
    #         for point in extra_points:    
    #             next_status_time = last_status_time + datetime.timedelta(seconds=1)

    #             delivery_statuses.add({
    #                 "id": order["id"],
    #                 "updatedAt": next_status_time.isoformat(),
    #                 "deliveryLat": str(point[0]),
    #                 "deliveryLon": str(point[1])
    #             })
    #             last_status_time = next_status_time
    #     except pyproj.exceptions.GeodError as e:
    #         print(e)

    # delivery_statuses.add({
    #     "id": order["id"],
    #     "updatedAt": (last_status_time + datetime.timedelta(seconds=1)).isoformat(),
    #     "deliveryLat": str(delivery_location[0]),
    #     "deliveryLon": str(delivery_location[1])
    # })


    other_statuses_to_publish = [item for item in other_statuses if parser.parse(item["updatedAt"]) < datetime.datetime.now()]

    for status in other_statuses_to_publish:
        producer.send('ordersStatuses', status, bytes(status["id"].encode("UTF-8")))
        other_statuses.remove(status)

    deliveries_to_publish = [item for item in delivery_statuses if parser.parse(item["updatedAt"]) < datetime.datetime.now()]

    for status in deliveries_to_publish:
        producer.send('deliveryStatuses', status, bytes(status["id"].encode("UTF-8")))
        delivery_statuses.remove(status)       


    events_processed += 1
    events_processed += (len(other_statuses_to_publish) + len(deliveries_to_publish)
    if events_processed % 1000 == 0:
        print(f"{str(datetime.datetime.now())} Flushing after {events_processed} events")
        producer.flush()

    time.sleep(random.randint(orderInterval/5, orderInterval)/1000)

