package cl.duoc.vidasalud.rabbitadmin;

import cl.duoc.vidasalud.rabbitadmin.config.NombresRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Punto de entrada del microservicio de administracion de RabbitMQ.
 * Habilita NombresRabbit para mantener centralizada la topologia protegida
 * sin literales dispersos en el codigo.
 */
@SpringBootApplication
@EnableConfigurationProperties(NombresRabbit.class)
public class MsVidasaludRabbitAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsVidasaludRabbitAdminApplication.class, args);
    }
}