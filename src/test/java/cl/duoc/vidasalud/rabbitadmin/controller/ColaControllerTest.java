package cl.duoc.vidasalud.rabbitadmin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.vidasalud.rabbitadmin.dto.ColaResponse;
import cl.duoc.vidasalud.rabbitadmin.dto.CrearColaRequest;
import cl.duoc.vidasalud.rabbitadmin.exception.OperacionNoPermitidaException;
import cl.duoc.vidasalud.rabbitadmin.exception.RecursoYaExisteException;
import cl.duoc.vidasalud.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de capa web para ColaController verificando codigos HTTP 201, 409, 403 y 400.
 */
@WebMvcTest(ColaController.class)
class ColaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitAdminService servicio;

    @Test
    @DisplayName("POST /api/rabbit/colas devuelve 201 al crear una cola nueva")
    void crearColaDevuelve201() throws Exception {
        when(servicio.crearCola(any(CrearColaRequest.class)))
                .thenReturn(new ColaResponse("vidasalud.pruebas", 0, 0, false));

        String json = """
                {
                  "nombre": "vidasalud.pruebas",
                  "durable": true,
                  "conDlq": true
                }
                """;

        mockMvc.perform(post("/api/rabbit/colas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("vidasalud.pruebas"));
    }

    @Test
    @DisplayName("POST /api/rabbit/colas devuelve 409 cuando la cola ya existe")
    void crearColaDuplicadaDevuelve409() throws Exception {
        when(servicio.crearCola(any(CrearColaRequest.class)))
                .thenThrow(new RecursoYaExisteException("La cola ya existe: vidasalud.pruebas"));

        String json = """
                {
                  "nombre": "vidasalud.pruebas",
                  "durable": true
                }
                """;

        mockMvc.perform(post("/api/rabbit/colas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409));
    }

    @Test
    @DisplayName("DELETE /api/rabbit/colas/{nombre} devuelve 403 al intentar borrar una cola protegida")
    void eliminarColaProtegidaDevuelve403() throws Exception {
        doThrow(new OperacionNoPermitidaException("No se permite eliminar la cola protegida: vidasalud.notificaciones"))
                .when(servicio).eliminarCola("vidasalud.notificaciones");

        mockMvc.perform(delete("/api/rabbit/colas/vidasalud.notificaciones"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));
    }

    @Test
    @DisplayName("POST /api/rabbit/colas devuelve 400 cuando el nombre esta vacio")
    void crearColaConNombreVacioDevuelve400() throws Exception {
        String json = """
                {
                  "nombre": "",
                  "durable": true
                }
                """;

        mockMvc.perform(post("/api/rabbit/colas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }
}