package cl.duoc.vidasalud.rabbitadmin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuracion de clientes de infraestructura hacia RabbitMQ.
 * Se declara RabbitAdmin explicitamente porque Spring Boot 4.1 no lo registra
 * por defecto, y se configura un RestClient para las operaciones de listado
 * y eliminacion de bindings que el protocolo AMQP no soporta directamente.
 */
@Configuration
public class RabbitAdminConfig {

    /**
     * Bean requerido para declarar, consultar, purgar y eliminar colas y exchanges por AMQP.
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory fabricaConexiones) {
        return new RabbitAdmin(fabricaConexiones);
    }

    /**
     * Cliente HTTP con autenticacion basica hacia el plugin Management de RabbitMQ (puerto 15672).
     */
    @Bean
    public RestClient clienteHttpRabbit(
            @Value("${spring.rabbitmq.username:vidasalud}") String usuario,
            @Value("${spring.rabbitmq.password:159}") String clave) {
        return RestClient.builder()
                .defaultHeaders(encabezados -> encabezados.setBasicAuth(usuario, clave))
                .build();
    }
}