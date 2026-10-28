package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class);
    private final AuthService authService;

    @Inject
    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            return Response.ok(authService.login(request)).build();
        } catch (InvalidLoginRequestException error) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", error.getMessage()))
                    .build();
        } catch (InvalidCredentialsException error) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("message", "Invalid user " + error.username() + " or password"))
                    .build();
        } catch (AuthenticationServiceException error) {
            LOGGER.error("Authentication processing failed", error);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Authentication failed"))
                    .build();
        }
    }
}
