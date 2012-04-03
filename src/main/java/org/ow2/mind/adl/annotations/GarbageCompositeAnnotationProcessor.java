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

import java.io.*;
import java.util.Map;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Node;
import org.objectweb.fractal.adl.interfaces.Interface;
import org.objectweb.fractal.adl.interfaces.InterfaceContainer;
import org.objectweb.fractal.adl.types.TypeInterface;
import org.ow2.mind.adl.annotation.ADLLoaderPhase;
import org.ow2.mind.adl.annotation.AbstractADLLoaderAnnotationProcessor;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.BindingContainer;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.ComponentContainer;
import org.ow2.mind.adl.ast.ImplementationContainer;
import org.ow2.mind.adl.ast.MindInterface;
import org.ow2.mind.adl.ast.Source;
import org.ow2.mind.annotation.Annotation;

/**
 * @author Julien TOUS
 */
public class GarbageCompositeAnnotationProcessor extends
AbstractADLLoaderAnnotationProcessor {

	private boolean garbage(final Annotation annotation,
			final Node node, final Definition composite,
			final ADLLoaderPhase phase, final Map<Object, Object> context) {
		boolean finishedGarbaging = false;
		DumpDotAnnotationProcessor plop = new DumpDotAnnotationProcessor();


		while(!finishedGarbaging) {
			finishedGarbaging = true;
			Component[] subComponents = ((ComponentContainer) composite).getComponents();
			for (Component subComponent : subComponents) {	
				Definition subCompDef;
				try {
					subCompDef = ASTHelper.getResolvedDefinition(subComponent
							.getDefinitionReference(), null, null);
					if (ASTHelper.isComposite(subCompDef))
					{
						finishedGarbaging = false;
						removeComposite( composite, subComponent);
						plop.processAnnotation( new DumpDot(),
								 node,  composite,
								 phase,  context);
						break;
					}
				} catch (ADLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return finishedGarbaging; 
	}

	private void removeComposite(Definition upperComposite, Component composite) throws ADLException{
		Definition compDef = ASTHelper.getResolvedDefinition(composite.getDefinitionReference(), null, null);
		Binding[] upperBindings = ((BindingContainer) upperComposite).getBindings();
		
		String compName = composite.getName();
		final Component[] subComponents = ((ComponentContainer) compDef).getComponents();
		for (Component subComponent : subComponents) {
			String subSubName = compName + "_" + subComponent.getName();
			subComponent.setName(subSubName);
			((ComponentContainer) upperComposite).addComponent(subComponent);
			((ComponentContainer) compDef).removeComponent(subComponent);
		}
		final Binding[] subBindings = ((BindingContainer) compDef)
				.getBindings();
		for (Binding subBinding : subBindings) {
			String subFromComp = subBinding.getFromComponent();
			String subToComp = subBinding.getToComponent();
			if (subFromComp.equals(Binding.THIS_COMPONENT)) {
				String thisItf = subBinding.getFromInterface();
				for (Binding binding : upperBindings) {
					if ((binding.getToComponent().equals(composite.getName())) && (binding.getToInterface().equals(thisItf))) {
						binding.setToComponent(compName + "_" + subToComp);
						binding.setToInterface(subBinding.getToInterface());
						((BindingContainer) compDef).removeBinding(subBinding);
					}
				}
			} else if (subToComp.equals(Binding.THIS_COMPONENT))  {
				//Treatement in the next for loop
			} else {
				((BindingContainer) upperComposite).addBinding(subBinding);
				((BindingContainer) compDef).removeBinding(subBinding);
				subBinding.setFromComponent(compName + "_" + subFromComp );
				subBinding.setToComponent(compName + "_" + subToComp);
			}
		}
		for (Binding binding : upperBindings) {
			String fromComp = binding.getFromComponent();
			String toComp = binding.getToComponent();
			if (fromComp.equals(compName)) {
				String thisItf = binding.getFromInterface();
				for (Binding subBinding : subBindings) {
					if ((subBinding.getToComponent().equals(Binding.THIS_COMPONENT)) && (subBinding.getToInterface().equals(thisItf))) {
						subBinding.setFromComponent(compName + "_" + subBinding.getFromComponent());
						subBinding.setToComponent(toComp);
						subBinding.setToInterface(binding.getToInterface());
						((BindingContainer) compDef).removeBinding(subBinding);
						((BindingContainer) upperComposite).addBinding(subBinding);
					}
				}
				((BindingContainer) upperComposite).removeBinding(binding);	
			}
		}
		((ComponentContainer) upperComposite).removeComponent(composite);
	}
	
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


		if (ASTHelper.isComposite(definition)) {
			garbage(annotation,
					 node,  definition,
					 phase,  context);
		}
		return null;
	}

}
