package com.papaymoni.middleware.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Configure Hibernate module
        Hibernate5Module hibernate5Module = new Hibernate5Module();
        hibernate5Module.configure(Hibernate5Module.Feature.FORCE_LAZY_LOADING, false);
        hibernate5Module.configure(Hibernate5Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS, true);
        mapper.registerModule(hibernate5Module);

        // Disable failing on empty beans (for proxy objects)
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        return mapper;
    }
}