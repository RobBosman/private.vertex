package nl.bransom.vertex;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.rx.java.RxHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

public class Main {

  private static final boolean CLUSTERED = false;

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    handleThat(42, i -> LOG.info("" + i));

    if (CLUSTERED) {
      final VertxOptions options = new VertxOptions();
      Vertx.clusteredVertx(options, res -> {
        if (res.succeeded()) {
          doVertex(res.result());
        } else {
          LOG.error("Error getting clustered vertx.", res.cause());
        }
      });
    } else {
      doVertex(Vertx.vertx());
    }
  }

  public static void doVertex(final Vertx vertx) {
    vertx.setTimer(10000, timerId -> {
      vertx.close();
      LOG.info("...and it's gone");
    });

    vertx.setPeriodic(2000, timerId -> {
      final EventBus eb = vertx.eventBus();
      eb.publish("news.uk.sport", "Yay! Someone kicked a ball");
      eb.send("news.uk.sport", "Yay! Someone kicked a ball across a patch of grass", res -> {
        if (res.succeeded()) {
          LOG.info("received reply: " + res.result().body());
        } else {
          LOG.warn("Error sending message: " + res.cause().getMessage());
        }
      });
    });

    final Observable<String> observableDeploymentId = RxHelper.observableHandler();
    observableDeploymentId.subscribe(value -> {
      final String deploymentId = value;
      LOG.debug("Deployment id from result is: " + deploymentId);
      vertx.setTimer(6000, timerId ->
          vertx.undeploy(deploymentId, res2 -> {
            if (res2.succeeded()) {
              LOG.info("shut down " + deploymentId + " OK");
            } else {
              LOG.error("shut down " + deploymentId + " failed", res2.cause());
            }
          })
      );
    });

    vertx.deployVerticle(MyVerticle.class.getName(), res -> {
      if (res.succeeded()) {
        final String deploymentId = res.result();
//        observableDeploymentId.; // TODO - ?
        observableDeploymentId.startWith(deploymentId);
        vertx.eventBus().publish("deployed verticle", deploymentId);
        LOG.info("Deployment id is: " + deploymentId);
      } else {
        LOG.error("Deployment failed.", res.cause());
      }
    });

    vertx.eventBus().consumer("deployed verticle", res -> {
      final String deploymentId = (String) res.body();
      LOG.debug("Deployment id from result is: " + deploymentId);
      vertx.setTimer(6000, timerId ->
          vertx.undeploy(deploymentId, res2 -> {
            if (res2.succeeded()) {
              LOG.info("shut down " + deploymentId + " OK");
            } else {
              LOG.error("shut down " + deploymentId + " failed", res2.cause());
            }
          })
      );
    });
  }

  public static <T> void handleThat(final T t, final Handler<T> handler) {
    handler.handle(t);
  }

  @FunctionalInterface
  public interface Handler<E> {
    void handle(E var1);
  }
}
