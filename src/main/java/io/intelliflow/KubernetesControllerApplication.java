package io.intelliflow;

import java.util.List;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.ShutdownEvent;

public class KubernetesControllerApplication implements QuarkusApplication {
	

	
	@Inject
	KubernetesClient client;

	@Inject
	SharedInformerFactory sharedInformerFactory;

	@Inject
	ResourceEventHandler<Node> nodeEventHandler;
	
	@Override
	public int run(String... args) throws Exception {

		try {
			client.nodes().list(new ListOptionsBuilder().withLimit(1L).build());
		} catch (KubernetesClientException ex) {
			System.out.println(ex.getMessage());
			return 1;
		}
		sharedInformerFactory.startAllRegisteredInformers().get();
		final var nodeHandler = sharedInformerFactory.getExistingSharedIndexInformer(Node.class);
		nodeHandler.addEventHandler(nodeEventHandler);
		Quarkus.waitForExit();
		return 0;

	}

	void onShutDown(@Observes ShutdownEvent event) {
		sharedInformerFactory.stopAllRegisteredInformers(true);
	}

	public static void main(String... args) {
		Quarkus.run(KubernetesControllerApplication.class, args);
	}

	@ApplicationScoped
	final static class KubernetesControllerApplicationConfig {

		@Inject
		KubernetesClient client;
		 
	    @Inject
	    @Channel("kube-out")
		Emitter<KubeEvent> emitter;
		
		@Singleton
		SharedInformerFactory sharedInformerFactory() {
			return client.informers();
		}

		@Singleton
		SharedIndexInformer<Node> nodeInformer(SharedInformerFactory factory) {
			return factory.sharedIndexInformerFor(Node.class, 0);
		}

		@Singleton
		SharedIndexInformer<Pod> podInformer(SharedInformerFactory factory) {
			return factory.sharedIndexInformerFor(Pod.class, 0);
		}

		@Singleton
		ResourceEventHandler<Node> nodeReconciler(SharedIndexInformer<Node> nodeInformer,
				SharedIndexInformer<Pod> podInformer) {
			return new ResourceEventHandler<>() {

				@Override
				public void onAdd(Node node) {
					// n.b. This is executed in the Watcher's WebSocket Thread
					// Ideally this should be executed by a Processor running in a dedicated thread
					// This method should only add an item to the Processor's queue.
					System.out.printf("node: %s%n", Objects.requireNonNull(node.getMetadata()).getName());
					podInformer.getIndexer().list().stream()
							.map(pod -> Objects.requireNonNull(pod.getMetadata()).getName())
							.forEach(podName -> System.out.printf("pod name: %s%n", podName));
					
					List<Service> services =  client.services().list().getItems();
					for(Service service : services) {
						
						//Service srv = client.services().inNamespace("default").withName("ifs-base-application").get();
						Integer nodePort = service.getSpec().getPorts().get(0).getNodePort();
						String  serviceName = service.getSpec().getExternalName();
						
						System.out.println("Service Name found : "+serviceName);
						System.out.println("NodePort found : "+nodePort);
						
						KubeEvent kubeEvent = new KubeEvent(serviceName, nodePort);
						
						emitter.send(kubeEvent);
						
					}

				}

				@Override
				public void onUpdate(Node oldObj, Node newObj) {
				}

				@Override
				public void onDelete(Node node, boolean deletedFinalStateUnknown) {
				}
			};
		}
	}

	

}
