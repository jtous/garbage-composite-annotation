package org.ow2.mind.adl;

import org.objectweb.fractal.adl.Loader;
import org.ow2.mind.adl.ADLFrontendModule;
import org.ow2.mind.inject.AbstractMindModule;

import com.google.inject.Key;
import com.google.inject.name.Names;

/**
 * Configure the ADL-Frontend Guice module.
 */
public class FlattenADLFrontendModule extends AbstractMindModule {

	/**
	 * Reminder of the standard ADLFrontendModule configuration, explaining the
	 * strategy used here, about the default loading chain:
	 * 
	 * Returns the {@link Key} used to bind the default loader chain. This module
	 * simply binds the {@link Loader} class to the default loader chain. But
	 * another module can {@link Modules#override override} this binding to add
	 * other loaders at the head of the chain. For instance :
	 * 
	 * <pre>
	 * bind(Loader.class).toChainStartingWith(MyLoader.class)
	 *     .followedBy(MyOtherLoader.class).endingWith(defaultLoaderKey());
	 * </pre>
	 * 
	 * @return the {@link Key} used to bind the default loader chain.
	 */
	public static Key<Loader> defaultLoaderKey() {
		return Key.get(Loader.class, Names.named("default-loader"));
	}

	/**
	 * Chain starts with flatten, but real calls to flattening happen AFTER coming
	 * back from the whole loading, see the FlattenLoader internals.
	 */

	protected void configureLoader() {
		bind(Loader.class).toChainStartingWith(FlattenLoader.class).endingWith(defaultLoaderKey());
	}

}
