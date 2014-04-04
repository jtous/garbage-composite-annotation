package org.ow2.mind.adl;

import java.util.Map;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.ow2.mind.adl.AbstractDelegatingLoader;
import org.ow2.mind.adl.annotations.ADLDumper;
import org.ow2.mind.adl.annotations.GarbageCompositeAnnotationProcessor;
import org.ow2.mind.adl.ast.ComponentContainer;

import com.google.inject.Inject;
import com.google.inject.Injector;

public class FlattenLoader extends AbstractDelegatingLoader {
	
	/*
	 * Works because our Loader is itself loaded by Google Guice.
	 */
	@Inject
	Injector injector;

	public Definition load(String name, Map<Object, Object> context)
			throws ADLException {
		
		// First call usual loaders
		final Definition d = clientLoader.load(name, context);
		
		// Handle only composites
		if (!(d instanceof ComponentContainer))
			return d;
		
		// Now flatten the loaded architecture tree
		// Ask Guice for an instance, for the fields to be injected (won't work otherwise)
		GarbageCompositeAnnotationProcessor processor = injector.getInstance(GarbageCompositeAnnotationProcessor.class);
		
		processor.runFlatten(d, context);
		
		// Debug: enable following lines to dump the flattened architecture
		ADLDumper dumper = injector.getInstance(ADLDumper.class);
		dumper.dump(d, context, false /* Do not try to keep annotations during the merge serialization */);
		
		// Return the resulting tree
		return d;
		
	}

}
