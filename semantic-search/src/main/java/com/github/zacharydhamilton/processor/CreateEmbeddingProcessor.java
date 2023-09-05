package com.github.zacharydhamilton.processor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.SaslConfigs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theokanning.openai.embedding.Embedding;

public class CreateEmbeddingProcessor {
    static String configType = (System.getenv("CONFIG_TYPE") != null) ? System.getenv("CONFIG_TYPE") : "FILE"; 
    static String configFile = (System.getenv("CONFIG_FILE") != null) ? System.getenv("CONFIG_FILE") : "../client.properties";
    static String sourceTopic = "products.metadata";
    static String sinkTopic = "products.embeddings";

    public static class CreateEmbedding implements MapFunction<String, String> {
        public String map(String value) {
            JsonArray array = new JsonArray();
            try {
                JsonObject json = JsonParser.parseString(value).getAsJsonObject();
                if (json.get("description") != null) {
                    List<Embedding> embeddings = EmbeddingUtils.create_embedding(json.get("description").toString());
                    for (Embedding embedding : embeddings) {
                        JsonArray sub_array = new JsonArray();
                        for (Double vector : embedding.getEmbedding()) {
                            sub_array.add(vector);
                        }
                        array.add(sub_array);
                    }
                    json.add("embedding", array);
                    return json.toString();
                }
            } catch (Exception exception) {
                System.out.println("Error generating embedding: "+ exception.getMessage() +", "+ value);
            }
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);

        KafkaSource<String> source = createKafkaSource();

        DataStream<String> stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafkaSource");

        DataStream<String> transformed = stream.map(new CreateEmbedding());

        DataStream<String> filtered = transformed.filter(new FilterFunction<String>() {
            @Override
            public boolean filter(String value) {
                return value != null;
            }
        });

        KafkaSink<String> sink = createKafkaSink();

        filtered.sinkTo(sink);

        env.execute("create-embeddings");
    }

    public static KafkaSource<String> createKafkaSource() throws IOException, ConfigException {
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
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(props.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG))
            .setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
            .setProperty(SaslConfigs.SASL_JAAS_CONFIG, props.getProperty(SaslConfigs.SASL_JAAS_CONFIG))
            .setProperty(SaslConfigs.SASL_MECHANISM, "PLAIN")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .setTopics(sourceTopic)
            .setGroupId("flink-reader")
            .build();
        return source;
    }

    public static KafkaSink<String> createKafkaSink() throws IOException, ConfigException {
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
        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers(props.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG))
            .setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
            .setProperty(SaslConfigs.SASL_JAAS_CONFIG, props.getProperty(SaslConfigs.SASL_JAAS_CONFIG))
            .setProperty(SaslConfigs.SASL_MECHANISM, "PLAIN")
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic(sinkTopic)
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
            .build(); 
         return sink;
    }
    
    /**
     * Check for the necessary configurations to initialize the client. If any are missing, fails. 
     * 
     * @throws ConfigException
     */
    private static void preInitChecks() throws ConfigException {
        ArrayList<String> requiredProps = new ArrayList<String>(Arrays.asList("BOOTSTRAP_SERVERS", "SASL_JAAS_CONFIG", "OPENAI_API_KEY"));
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
            throw new IOException("Config file (" + file + ") does not exist or was not found.");
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            props.load(inputStream);
        }
    }
}
