package com.github.zacharydhamilton.producer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;

import com.google.gson.JsonObject;

public class MetadataProducer {
    static String configType = (System.getenv("CONFIG_TYPE") != null) ? System.getenv("CONFIG_TYPE") : "FILE"; 
    static String configFile = (System.getenv("CONFIG_FILE") != null) ? System.getenv("CONFIG_FILE") : "../client.properties";
    static String dataFile = (System.getenv("METADATA_FILE") != null) ? System.getenv("METADATA_FILE") : "../data/video_game_products.json.gz";
    static String topic = "products.metadata";
    static String clientId = "metadata-producer";

    public static void main(String[] args) throws IOException {
        ArrayList<JsonObject> metadata = GzipReader.readJson(dataFile);
        KafkaProducer<String, String> producer = createProducer();
        for (JsonObject json : metadata) {
            ProducerRecord<String, String> record = new ProducerRecord<String,String>(topic, json.get("asin").toString(), json.toString());
            producer.send(record);
        }
        producer.flush();
    }

    public static KafkaProducer<String, String> createProducer() throws IOException {
        Properties props = new Properties();
        if (configType.equals("FILE")) {
            addPropsFromFile(props, configFile);
        } else {
            preInitChecks();
            props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, System.getenv("BOOTSTRAP_SERVERS"));
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, System.getenv("SASL_JAAS_CONFIG"));
            props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        }
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); 
        return new KafkaProducer<String, String>(props);
    }

    /**
     * Check for the necessary configurations to initialize the client. If any are missing, fails. 
     * 
     * @throws ConfigException
     */
    private static void preInitChecks() throws ConfigException {
        ArrayList<String> requiredProps = new ArrayList<String>(Arrays.asList("BOOTSTRAP_SERVERS", "SASL_JAAS_CONFIG", "METADATA_FILE"));
        ArrayList<String> missingProps = new ArrayList<String>();
        for (String prop : requiredProps) {
            if (System.getenv(prop).equals(null)) {
                missingProps.add(prop);
            }
        }
        if (missingProps.size() > 0) {
            throw new ConfigException("Missing required properties: " + missingProps.toString());
        }
    }

    /**
     * Load properties from an application properties file.
     * 
     * @param props - An existing Properties object to add the properties to.
     * @param file - An existing file containing properties to add to the Properties object. 
     * @throws IOException
     */
    private static void addPropsFromFile(Properties props, String file) throws IOException {
        if (!Files.exists(Paths.get(file))) {
            System.out.println("Current working environment: " + System.getProperty("user.dir"));
            throw new IOException("Config file (" + file + ") does not exist or was not found.");
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            props.load(inputStream);
        }
    }
}
