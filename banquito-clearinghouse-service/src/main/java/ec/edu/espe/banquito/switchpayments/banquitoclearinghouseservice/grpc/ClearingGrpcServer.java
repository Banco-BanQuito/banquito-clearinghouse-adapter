package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
public class ClearingGrpcServer {

    private final int port;
    private final ClearingGrpcService clearingGrpcService;
    private Server server;

    public ClearingGrpcServer(@Value("${clearing.grpc.port:9094}") int port,
                              ClearingGrpcService clearingGrpcService) {
        this.port = port;
        this.clearingGrpcService = clearingGrpcService;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder
                .forPort(port)
                .addService(clearingGrpcService)
                .build()
                .start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
    }
}
