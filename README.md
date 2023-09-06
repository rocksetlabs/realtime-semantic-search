# realtime-semantic-search
Code for Confluent and Rockset webinar showing how to leverage real time data with vector search


### Confluent

* Terraform
* `docker compose up -d`
* Create an update.
    - `docker compose exec updater java -cp /usr/app/semantic-search-1.0.0.jar com.github.zacharydhamilton.producer.MetadataProducer topic=products.metadata clientId=metadata-producer metadataFile=/usr/app/data/battle_hunter_updated.json.gz`

docker compose exec updater java -cp /usr/app/semantic-search-1.0.0.jar com.github.zacharydhamilton.producer.MetadataProducer topic=products.metadata clientId=metadata-producer metadataFile=/usr/app/data/battle_hunter_orginal.json.gz

docker compose exec updater java -cp /usr/app/semantic-search-1.0.0.jar com.github.zacharydhamilton.producer.MetadataProducer topic=products.metadata clientId=metadata-producer metadataFile=/usr/app/data/battle_hunter_updated.json.gz