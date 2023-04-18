module org.nasdanika.architecture.docs {
		
	requires transitive org.nasdanika.html.model.app;
	requires transitive org.nasdanika.html.ecore.gen;
	requires org.eclipse.emf.ecore.xmi;
	
	requires org.nasdanika.architecture.c4;
	
	requires org.nasdanika.architecture.cloud.azure.compute;
	requires org.nasdanika.architecture.cloud.azure.networking;
	requires org.nasdanika.architecture.cloud.azure.storage;
	
	requires org.nasdanika.architecture.containers.docker;
	requires org.nasdanika.architecture.containers.kubernetes;
	requires org.nasdanika.architecture.containers.helm;
	
}
