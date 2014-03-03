/**
 * Copyright (C) 2012 Schneider Electric
 *
 * This file is part of "Mind Compiler" is free software: you can redistribute 
 * it and/or modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: mind@ow2.org
 *
 * Authors: Julien TOUS
 * Contributors:
 */

package org.ow2.mind.adl.annotations;

import static org.ow2.mind.adl.parameter.ast.ParameterASTHelper.turnsToArgumentContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.Node;
import org.objectweb.fractal.adl.NodeFactory;
import org.objectweb.fractal.adl.merger.MergeException;
import org.objectweb.fractal.adl.merger.NodeMerger;
import org.objectweb.fractal.adl.util.FractalADLLogManager;
import org.ow2.mind.adl.annotation.ADLLoaderPhase;
import org.ow2.mind.adl.annotation.AbstractADLLoaderAnnotationProcessor;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.BindingContainer;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.ComponentContainer;
import org.ow2.mind.adl.ast.DefinitionReference;
import org.ow2.mind.adl.parameter.ast.Argument;
import org.ow2.mind.annotation.Annotation;
import org.ow2.mind.annotation.AnnotationHelper.AnnotationDecoration;
import org.ow2.mind.error.ErrorManager;
import org.ow2.mind.value.ast.Reference;
import org.ow2.mind.value.ast.Value;

import com.google.inject.Inject;

/**
 * @author Julien TOUS
 * @contributor Stephane SEYVOZ
 */
public class GarbageCompositeAnnotationProcessor extends
AbstractADLLoaderAnnotationProcessor {

	@Inject
	protected NodeFactory 	nodeFactoryItf;

	@Inject
	protected NodeMerger 	nodeMergerItf;

	@Inject
	protected Loader 		loaderItf;

	@Inject
	protected ErrorManager   errorManager;


	private Map<Object, Object> context;
	private ADLLoaderPhase phase;

	/**
	 * Logger.
	 */
	private static Logger logger = FractalADLLogManager.getLogger("GCt");

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.ow2.mind.adl.annotation.ADLLoaderAnnotationProcessor#processAnnotation
	 * (org.ow2.mind.annotation.Annotation, org.objectweb.fractal.adl.Node,
	 * org.objectweb.fractal.adl.Definition,
	 * org.ow2.mind.adl.annotation.ADLLoaderPhase, java.util.Map)
	 */
	public Definition processAnnotation(final Annotation annotation,
			final Node node, final Definition definition,
			final ADLLoaderPhase phase, final Map<Object, Object> context)
					throws ADLException {
		assert annotation instanceof GarbageComposite;

		this.phase = phase;
		this.context = context;
		
		if (ASTHelper.isComposite(definition))
			runFlatten(definition);
		
		ADLDumper dumper = null;
		if (((GarbageComposite) annotation).dumpADL)
			dumper = new ADLDumper(definition, context, ((GarbageComposite) annotation).dumpAnnotations);
		
		return null;
	}
	
	/**
	 * Init the flatten recursion.
	 */
	public void runFlatten(Definition definition) {

		// new data containers: their reference will be provided to the "flatten" method to be
		// filled at each level of the recursion
		List<Component> retComponents	= new ArrayList<Component>();
		List<Binding> retBindings 		= new ArrayList<Binding>();

		// go recursive
		flatten(definition, retComponents, retBindings);

		// in the end we obtain huge lists of components and everything as already been propagated
		// so we can free those lists
		retComponents = null;
		retBindings = null;
	}

	/**
	 * Flatten the components tree with bindings merge.
	 * We need to care about the 3 levels implied by the manipulation:
	 * - Level 0 is the container of all
	 * - Level 1 is the level where we want to remove composites and uplift their sub-components
	 * - Level 2 is the level where the sub-components and bindings are taken from, to move to level 1
	 * 
	 * TODO: Check if we should not propagate annotations somehow ? but with which policy ?
	 * 
	 * @param level0Definition
	 * @param componentsToUpperComposite
	 * @param bindingsToUpperComposite
	 */
	private void flatten(Definition level0Definition,
			List<Component> componentsToUpperComposite,
			List<Binding> bindingsToUpperComposite) {

		Definition currLevel1CompDef = null;

		assert level0Definition instanceof ComponentContainer;
		assert level0Definition instanceof BindingContainer;

		ComponentContainer 	level0DefAsComponentContainer 	= ((ComponentContainer) level0Definition);
		BindingContainer 	level0DefAsBindingContainer 	= ((BindingContainer) level0Definition);

		// non-modifiable initial list
		final List<Component> 	level1ComponentsList 	= Arrays.asList(level0DefAsComponentContainer.getComponents());
		
		// this list will be modified on the fly
		List<Binding> 	level1BindingsList 	= null;

		// iteration utility
		List<Argument> 	currLevel1CompDefArguments 		= null;
		List<Argument> 	currLevel2CompDefArguments 		= null;

		// new data containers: their reference will be provided to the "flatten" method to be filled at each
		// level of the recursion
		List<Component>	retLevel2Components	= null;
		List<Binding> 	retLevel2Bindings 	= null;

		// new data containers
		List<Binding> 	level2SrcThisBindingsList		= null;
		List<Binding> 	level2TgtThisBindingsList		= null;
		List<Binding> 	level2BasicBindingsList			= null;

		String currLevel1InstanceName = null;

		for (Component currLevel1Instance : level1ComponentsList) {

			currLevel1InstanceName = currLevel1Instance.getName();
			
			// refresh the binding list in case the previous iteration/recursion led to bindings creation/removal
			level1BindingsList 	= Arrays.asList(level0DefAsBindingContainer.getBindings());
			
			retLevel2Components	= new ArrayList<Component>();
			retLevel2Bindings 	= new ArrayList<Binding>();
			
			//--------------------
			// Preparing elements
			//--------------------

			try {
				currLevel1CompDef = resolveComponentDefinition(currLevel1Instance);
			} catch (ADLException e) {
				// just don't add to the list
				logger.warning("couldn't resolve " + currLevel1Instance.getName() + " definition - skip");
				// cleanup just in case
				currLevel1CompDef = null;
				// all other elements will shift to the left according to remove doc
				continue;
			}

			// if null we are probably dealing with a template on the after check phase, so we ignore/skip it
			// the annotation will be re-processed on the template instantiation phase with types merged anyway 
			if (currLevel1CompDef == null) {
				if (phase == ADLLoaderPhase.AFTER_CHECKING)
					logger.warning("couldn't resolve " + currLevel1Instance.getName() + " definition in After Checking phase, probably a template - skip");
				else if (phase == ADLLoaderPhase.AFTER_TEMPLATE_INSTANTIATE)
					// inspired from ParametricDefinitionReferenceResolver.java: 123
					logger.warning("couldn't resolve " + currLevel1Instance.getName() + " definition in After Template Instantiate phase, abnormal situation - skip (but flattening result may be hazardous !)");
				
				// in any case... do nothing and go to the next level 1 component
				continue;
			}

			// the PROVIDED arguments at the instantiation time are NOT in the definition (ex: contains Comp2(8) as comp;) but the definition REFERENCE (value 8 in example) 
			currLevel1CompDefArguments = Arrays.asList(turnsToArgumentContainer(currLevel1Instance.getDefinitionReference(), nodeFactoryItf, nodeMergerItf).getArguments());

			if (ASTHelper.isComposite(currLevel1CompDef)) {

				//-----------
				// Recursion
				//-----------

				flatten(currLevel1CompDef, retLevel2Components, retLevel2Bindings);

				//------------------------
				// Components: Level Up !
				//------------------------

				// clone all level 2 components in the composite as level 1 (level up !)
				for (Component currLevel2Comp : retLevel2Components) {

					Definition currLevel2CompDef = null;

					try {
						currLevel2CompDef = resolveComponentDefinition(currLevel2Comp);
					} catch (ADLException e) {
						// just don't add to the list
						logger.warning("couldn't resolve " + currLevel1Instance.getName() + " definition - skip");
						// cleanup just in case
						continue;
					}

					// the PROVIDED arguments at the instantiation time are NOT in the definition (ex: contains Comp2(8) as comp;) but the definition REFERENCE (value 8 in example)
					currLevel2CompDefArguments = Arrays.asList(turnsToArgumentContainer(currLevel2Comp.getDefinitionReference(), nodeFactoryItf, nodeMergerItf).getArguments());

					// propagate argument values as level 1 composite will be removed (and its arguments with it)
					for (Argument currLevel1Argument : currLevel1CompDefArguments) {
						for (Argument currLevel2Argument : currLevel2CompDefArguments) {
							Value argValueObj = currLevel2Argument.getValue();
							String argValueStr;

							// different types of arguments can be found
							if (argValueObj instanceof Reference)
								argValueStr = ((Reference) argValueObj ).getRef();
							else
								argValueStr = ((Value) argValueObj).toString(); 

							// if level 1 and 2 arguments match, do the value propagation accordingly
							if (currLevel1Argument.getName().equals(argValueStr))
								currLevel2Argument.setValue(currLevel1Argument.getValue());
						}
					}					

					Component newComp = ASTHelper.newComponent(nodeFactoryItf,
							currLevel1InstanceName + "_" + currLevel2Comp.getName(), currLevel2Comp.getDefinitionReference());

					ASTHelper.setResolvedComponentDefinition(newComp, currLevel2CompDef);

					level0DefAsComponentContainer.addComponent(newComp);
				}

				//-------------------------
				// Update level 1 bindings
				//-------------------------

				// containers
				level2SrcThisBindingsList	= new ArrayList<Binding>();
				level2TgtThisBindingsList	= new ArrayList<Binding>();
				level2BasicBindingsList		= new ArrayList<Binding>();

				// filter by type
				discriminateBindings(retLevel2Bindings, level2SrcThisBindingsList, level2TgtThisBindingsList, level2BasicBindingsList);

				for (Binding currLevel1Binding : level1BindingsList) {
					// handle target current sub component
					if (currLevel1Binding.getToComponent().equals(currLevel1InstanceName)) {
						// Level 2: "binds this.itf1 to comp.itf2" (-> in)
						for (Binding currLevel2ThisBinding : level2SrcThisBindingsList) {
							if (currLevel1Binding.getToInterface().equals(currLevel2ThisBinding.getFromInterface())
									// check compatibility of collection number information
									&& ((currLevel1Binding.getToInterfaceNumber() != null && currLevel2ThisBinding.getFromInterfaceNumber() != null)
											&& (currLevel1Binding.getToInterfaceNumber().equals(currLevel2ThisBinding.getFromInterfaceNumber()))
										|| (currLevel1Binding.getToInterfaceNumber() == null && currLevel2ThisBinding.getFromInterfaceNumber() == null))) {
								/* 
								 * Calculate the new name of the "leveled-up" target component and set it as new binding
								 * target, modify the level 1 bindings since bindings are SOURCE-DRIVEN.
								 */
								currLevel1Binding.setToComponent(currLevel1InstanceName + "_" + currLevel2ThisBinding.getToComponent());
								// inner bindings source and destination may be different
								currLevel1Binding.setToInterface(currLevel2ThisBinding.getToInterface());
								// handle collections
								currLevel1Binding.setToInterfaceNumber(currLevel2ThisBinding.getToInterfaceNumber());
								
								/*
								 *  no other info update since we really modify the composite definition (even if the composite
								 *  is used in other places in the architecture, the sub-modifications would be identical, so we allow
								 *  such modification). TODO: check if this is true according to templates ?
								 */
								
								// no binding level-up since we modify existing ones (source-driven)
							}
						}
					}
					if (currLevel1Binding.getFromComponent().equals(currLevel1InstanceName)) {
						// Level2: binds "comp.itf1 to this.itf2" (-> out)
						for (Binding currLevel2ThisBinding : level2TgtThisBindingsList) {
							if (currLevel1Binding.getFromInterface().equals(currLevel2ThisBinding.getToInterface())
									// check compatibility of collection number information
									&& (((currLevel1Binding.getFromInterfaceNumber() != null && currLevel2ThisBinding.getToInterfaceNumber() != null)
											&& currLevel1Binding.getFromInterfaceNumber().equals(currLevel2ThisBinding.getToInterfaceNumber()))
										|| (currLevel1Binding.getFromInterfaceNumber() == null && currLevel2ThisBinding.getToInterfaceNumber() == null))) {
								
								/* 
								 * we do not wish to modify the level 2 definition (since another composite may use it),
								 * in our source-driven approach
								 */
								Binding newLevel1Binding = ASTHelper.newBinding(nodeFactoryItf);
								
								/* 
								 * Calculate the new name of the "leveled-up" target component and set it as new binding
								 * target, modify the level 2 bindings since bindings are SOURCE-DRIVEN.
								 */
								newLevel1Binding.setFromComponent(currLevel1InstanceName + "_" + currLevel2ThisBinding.getFromComponent());
								// destination in level 1 (no renaming)
								newLevel1Binding.setToComponent(currLevel1Binding.getToComponent());
								// inner bindings source and destination may be different
								newLevel1Binding.setToInterface(currLevel1Binding.getToInterface());
								// handle collections
								newLevel1Binding.setToInterfaceNumber(currLevel1Binding.getToInterfaceNumber());
								
								// duplicate the other currLevel2ThisBinding info
								newLevel1Binding.setFromInterface(currLevel2ThisBinding.getFromInterface());
								newLevel1Binding.setFromInterfaceNumber(currLevel2ThisBinding.getFromInterfaceNumber());
								
								// transfer level 1 annotation to the new level 1 bindings
								applyAnnotations(currLevel1Binding, newLevel1Binding);
								
								// binding level-up ! (source-driven)
								
								// remove level 1 binding
								level0DefAsBindingContainer.removeBinding(currLevel1Binding);
								// add the new one
								level0DefAsBindingContainer.addBinding(newLevel1Binding);
							}
						}
					}
				}

				/*
				 * for standard bindings (no 'this'), clone them, update the names and add the new binding to level 1
				 * we still do not want to modify the sub-component definition since it can be used in other parts of the
				 * architecture
				 */
				for (Binding currLevel2ThisBinding : level2BasicBindingsList) {
					Binding newBinding = ASTHelper.newBinding(nodeFactoryItf);

					newBinding.setFromComponent(currLevel1InstanceName + "_" + currLevel2ThisBinding.getFromComponent());
					newBinding.setToComponent(currLevel1InstanceName + "_" + currLevel2ThisBinding.getToComponent());
					newBinding.setFromInterface(currLevel2ThisBinding.getFromInterface());
					newBinding.setFromInterfaceNumber(currLevel2ThisBinding.getFromInterfaceNumber());
					newBinding.setToInterface(currLevel2ThisBinding.getToInterface());
					newBinding.setToInterfaceNumber(currLevel2ThisBinding.getToInterfaceNumber());

					// new binding goes to level 0
					level0DefAsBindingContainer.addBinding(newBinding);
				}

				//---------------------------------------------------
				// Remove the current level 1 composite from level 0
				//---------------------------------------------------

				level0DefAsComponentContainer.removeComponent(currLevel1Instance);	
			}
		}

		//-------------------------------------------------------------------------------------------------------------
		// Populate the upper return lists needed for the recursion, with the freshly modified components and bindings
		//-------------------------------------------------------------------------------------------------------------

		componentsToUpperComposite.addAll(Arrays.asList(level0DefAsComponentContainer.getComponents()));
		bindingsToUpperComposite.addAll(Arrays.asList(level0DefAsBindingContainer.getBindings()));
	}

	private Definition resolveComponentDefinition(Component component) throws ADLException {
		DefinitionReference	currCompDefRef = component.getDefinitionReference();
		if (currCompDefRef == null)
			return null;
		return ASTHelper.getResolvedDefinition(currCompDefRef, loaderItf, this.context);
	}

	/**
	 * For all bindings in the list, filter the inbound and outbound ones (bound to 'this') and return them.
	 * @param bindingsList
	 */
	private void discriminateBindings(List<Binding> bindingsList, List<Binding> srcThisBindingsList, List<Binding> tgtThisBindingsList, List<Binding> basicBindingsList) {

		boolean isSrcThis = false;
		boolean isTgtThis = false;

		for (Binding binding : bindingsList) {
			if (binding.getFromComponent() == Binding.THIS_COMPONENT) {
				srcThisBindingsList.add(binding);
				isSrcThis = true;
			}
			if (binding.getToComponent() == Binding.THIS_COMPONENT) {
				tgtThisBindingsList.add(binding);
				isTgtThis = true;
			}
			if (!isSrcThis && !isTgtThis)
				basicBindingsList.add(binding);
			
			// cleanup at each iteration for each next binding
			isSrcThis = false;
			isTgtThis = false;
		}

	}
	
	/**
	 * Merging annotations from the extension node and the target definition node.
	 * In the case of duplicates, the merge strategy is to override with the extension annotation.
	 * 
	 * TODO: duplicate nodes ? is it really needed ?
	 * 
	 * @param The extension node from which to get annotations from.
	 * @param The definition node to apply annotations to.
	 * @throws ADLException 
	 */
	private void applyAnnotations(Node source, Node destination) {

		// Let's merge the annotations, taking shorcuts inspired from AnnotationHelper !

		AnnotationDecoration extDecoration = (AnnotationDecoration) source.astGetDecoration("annotations");
		if (extDecoration == null)
			return;

		AnnotationDecoration nDecoration = (AnnotationDecoration) destination.astGetDecoration("annotations");
		if (nDecoration == null) {
			nDecoration = new AnnotationDecoration();
		}

		AnnotationDecoration mergeResultDecoration = null;

		try {
			mergeResultDecoration = (AnnotationDecoration) nDecoration.mergeDecoration(extDecoration);
		} catch (MergeException e) {
			// This exception will never happen since there is not a single branch of code where it is thrown !
			e.printStackTrace();
		}

		if (mergeResultDecoration != null)
			destination.astSetDecoration("annotations", mergeResultDecoration);

	}

}
