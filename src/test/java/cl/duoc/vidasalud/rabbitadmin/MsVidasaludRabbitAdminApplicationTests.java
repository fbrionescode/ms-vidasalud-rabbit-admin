package cl.duoc.vidasalud.rabbitadmin;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Prueba de carga del contexto de Spring simulando la conexion AMQP.
 */
@SpringBootTest
class MsVidasaludRabbitAdminApplicationTests {

    @MockitoBean
    private ConnectionFactory fabricaConexiones;

    @Test
    void contextLoads() {
    }
}