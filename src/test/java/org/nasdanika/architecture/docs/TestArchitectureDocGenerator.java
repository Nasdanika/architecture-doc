package org.nasdanika.architecture.docs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.nasdanika.architecture.c4.C4Package;
import org.nasdanika.architecture.cloud.azure.compute.ComputePackage;
import org.nasdanika.architecture.cloud.azure.networking.NetworkingPackage;
import org.nasdanika.architecture.cloud.azure.storage.StoragePackage;
import org.nasdanika.architecture.containers.docker.DockerPackage;
import org.nasdanika.architecture.containers.helm.HelmPackage;
import org.nasdanika.architecture.containers.kubernetes.KubernetesPackage;
import org.nasdanika.common.Context;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.NullProgressMonitor;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.graph.Element;
import org.nasdanika.graph.emf.EObjectNode;
import org.nasdanika.graph.emf.Util;
import org.nasdanika.graph.processor.IntrospectionLevel;
import org.nasdanika.graph.processor.ProcessorInfo;
import org.nasdanika.html.ecore.gen.processors.EcoreNodeProcessorFactory;
import org.nasdanika.html.model.app.Label;
import org.nasdanika.html.model.app.graph.Registry;
import org.nasdanika.html.model.app.graph.URINodeProcessor;
import org.nasdanika.html.model.app.graph.emf.EObjectReflectiveProcessorFactory;
import org.nasdanika.common.Diagnostic;

/**
 * Tests Ecore -> Graph -> Processor -> actions generation
 * @author Pavel
 *
 */
public class TestArchitectureDocGenerator {
	
	protected Object eKeyToPathSegment(EAttribute keyAttribute, Object keyValue) {
		return keyValue;
	}	
	
	protected String path(EObjectNode source, EObjectNode target, EReference reference, int index) {
		if (reference.getEKeys().isEmpty() && target.getTarget() instanceof ENamedElement && reference.isUnique()) {
			// TODO - eOperation - hash of signature, copy from the old code.
			
			return ((ENamedElement) target.getTarget()).getName();
		}
		return Util.path(source, target, reference, index, this::eKeyToPathSegment);
	}
	
	@Test
	public void testArchitectureDocGenerator() throws IOException {		
		List<EPackage> architectureEPackages = Arrays.asList(
				org.nasdanika.architecture.core.CorePackage.eINSTANCE); //,
//				C4Package.eINSTANCE,
//				org.nasdanika.architecture.cloud.azure.core.CorePackage.eINSTANCE, 
//				ComputePackage.eINSTANCE, 
//				NetworkingPackage.eINSTANCE, 
//				StoragePackage.eINSTANCE,
//				DockerPackage.eINSTANCE,
//				KubernetesPackage.eINSTANCE,
//				HelmPackage.eINSTANCE);
		
//		List<EPackage> ePackages = Arrays.asList(
//				org.nasdanika.ncore.NcorePackage.eINSTANCE,
//				ExecPackage.eINSTANCE,
//				org.nasdanika.exec.content.ContentPackage.eINSTANCE,
//				org.nasdanika.exec.resources.ResourcesPackage.eINSTANCE,
//				HtmlPackage.eINSTANCE, 
//				BootstrapPackage.eINSTANCE, 
//				AppPackage.eINSTANCE);
				
		List<EPackage> topLevelPackages = architectureEPackages.stream().filter(ep -> ep.getESuperPackage() == null).collect(Collectors.toList());
		List<EObjectNode> nodes = Util.load(topLevelPackages, this::path);
		
		Context context = Context.EMPTY_CONTEXT;		
		EcoreNodeProcessorFactory ecoreNodeProcessorFactory = new EcoreNodeProcessorFactory(context);		
		EObjectReflectiveProcessorFactory processorFactory = new EObjectReflectiveProcessorFactory(IntrospectionLevel.DECLARED, ecoreNodeProcessorFactory);
		
		ProgressMonitor progressMonitor = new NullProgressMonitor(); // new PrintStreamProgressMonitor();
		org.nasdanika.html.model.app.graph.Registry<URI> registry = processorFactory.createProcessors(nodes, progressMonitor);
		
		List<Throwable> failures = registry
				.getProcessorInfoMap()
				.values()
				.stream()
				.flatMap(pi -> pi.getFailures().stream())
				.collect(Collectors.toList());
		
		if (!failures.isEmpty()) {
			NasdanikaException ne = new NasdanikaException("Theres's been " + failures.size() +  " failures during processor creation: " + failures);
			for (Throwable failure: failures) {
				ne.addSuppressed(failure);
			}
			throw ne;
		}						
		
		Map<String, URINodeProcessor> topLevelProcessors = new HashMap<>();
		Collection<Throwable> resolveFailures = new ArrayList<>();
		
		for (EPackage topLevelPackage: topLevelPackages) {
			for (Entry<Element, ProcessorInfo<Object, Registry<URI>>> re: registry.getProcessorInfoMap().entrySet()) {
				Element element = re.getKey();
				if (element instanceof EObjectNode) {
					EObjectNode eObjNode = (EObjectNode) element;
					EObject target = eObjNode.getTarget();
					if (target == topLevelPackage) {
						ProcessorInfo<Object, Registry<URI>> info = re.getValue();
						Object processor = info.getProcessor();
						if (processor instanceof URINodeProcessor) {
							URINodeProcessor uriNodeProcessor = (URINodeProcessor) processor;
							uriNodeProcessor.resolve(URI.createURI("https://architecture.nasdanika.org/" + topLevelPackage.getName() + "/"));
							topLevelProcessors.put(topLevelPackage.getName(), uriNodeProcessor);
						}
					}
				}
			}			
		}
		
		if (!resolveFailures.isEmpty()) {
			NasdanikaException ne = new NasdanikaException("Theres's been " + resolveFailures.size() +  " failures during URI resolution: " + resolveFailures);
			for (Throwable failure: resolveFailures) {
				ne.addSuppressed(failure);
			}
			throw ne;
		}								
		
		File actionModelsDir = new File("target\\actions");
		actionModelsDir.mkdirs();

		ResourceSet actionModelsResourceSet = new ResourceSetImpl();		
		actionModelsResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		
		Consumer<Diagnostic> diagnosticConsumer = d -> d.dump(System.out, 0);
		for (Entry<String, URINodeProcessor> processorEntry: topLevelProcessors.entrySet()) {			
			Collection<Label> labels = processorEntry.getValue().createLabelsSupplier().call(progressMonitor, diagnosticConsumer);
			if (labels != null && !labels.isEmpty()) {
				URI actionModelUri = URI.createFileURI(new File(actionModelsDir, processorEntry.getKey() + ".xml").getAbsolutePath());				
				Resource actionModelResource = actionModelsResourceSet.createResource(actionModelUri);
				actionModelResource.getContents().addAll(labels);				
				actionModelResource.save(null);						
			}
			System.out.println(labels);
		}
		
	}

}
