docker-compose -f docker-app-compose.yml down
docker-compose down
docker-compose up -d

mvn clean install -DskipTests

cd order-service
mvn spring-boot:build-image -DskipTests \
  -Dspring-boot.build-image.imageName=order-service

cd ../

docker-compose -f docker-app-compose.yml up -d