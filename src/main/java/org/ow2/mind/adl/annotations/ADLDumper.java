/**
 * Copyright (C) 2013 Schneider Electric
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
 * Authors: Stephane SEYVOZ
 * Contributors:
 */

package org.ow2.mind.adl.annotations;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.Node;
import org.objectweb.fractal.adl.interfaces.Interface;
import org.objectweb.fractal.adl.interfaces.InterfaceContainer;
import org.objectweb.fractal.adl.types.TypeInterface;
import org.objectweb.fractal.adl.util.FractalADLLogManager;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Binding;
import org.ow2.mind.adl.ast.BindingContainer;
import org.ow2.mind.adl.ast.Component;
import org.ow2.mind.adl.ast.ComponentContainer;
import org.ow2.mind.adl.ast.DefinitionReference;
import org.ow2.mind.adl.ast.MindInterface;
import org.ow2.mind.adl.parameter.ast.Argument;
import org.ow2.mind.adl.parameter.ast.ArgumentContainer;
import org.ow2.mind.annotation.Annotation;
import org.ow2.mind.annotation.AnnotationHelper;
import org.ow2.mind.io.OutputFileLocator;
import org.ow2.mind.value.ast.BooleanLiteral;
import org.ow2.mind.value.ast.CompoundValue;
import org.ow2.mind.value.ast.CompoundValueField;
import org.ow2.mind.value.ast.NullLiteral;
import org.ow2.mind.value.ast.NumberLiteral;
import org.ow2.mind.value.ast.Reference;
import org.ow2.mind.value.ast.StringLiteral;
import org.ow2.mind.value.ast.Value;

import com.google.inject.Inject;

/**
 * @author Stephane SEYVOZ
 * Note: Maybe this should be rewritten using StringTemplates.
 */
public class ADLDumper {

	private File outputFile = null;
	private FileWriter outputFileWriter = null;
	private BufferedWriter outputFileBufferedWriter = null; 

	private boolean dumpAnnotations = false;

	/**
	 * Logger.
	 */
	private static Logger logger = FractalADLLogManager.getLogger("ADLDumper");

	@Inject
	protected Loader        loaderItf;

	@Inject
	protected OutputFileLocator outputFileLocatorItf;

	private String tab = "\t";
	private String semicolon = ";";

	public void dump(final Definition rootDefinition, Map<Object, Object> context, boolean dumpAnnotations) {

		this.dumpAnnotations = dumpAnnotations;

		// short name (no package) + _flat suffix
		String filePath = rootDefinition.getName().replace('.', '/') + "_flat.adl";

		try {
			initFile(filePath, context);
			writeHeader(rootDefinition);

			run(rootDefinition, context);

			writeFooter();
			flushBufferCloseFile();

		} catch (IOException e) {
			logger.warning("Could not dump flattened " + rootDefinition.getName() + " ADL to file - File creation/writing error !");
			return;
		}
	}

	private void flushBufferCloseFile() throws IOException {
		outputFileBufferedWriter.close();
	}

	/**
	 * If file doesn't exist create it
	 * @param outputFile 
	 * @throws IOException 
	 */
	private void initFile(String filePath, Map<Object, Object> context) throws IOException {
		outputFile = outputFileLocatorItf.getCSourceOutputFile(filePath.startsWith("/") ? filePath : "/" + filePath, context);
		if (outputFile != null && !outputFile.exists())
			outputFile.createNewFile();

		outputFileWriter = new FileWriter(outputFile, false);
		outputFileBufferedWriter = new BufferedWriter(outputFileWriter);
	}

	public void writeHeader(Definition rootDefinition) throws IOException {
		outputFileBufferedWriter.write("/**");
		outputFileBufferedWriter.newLine();

		outputFileBufferedWriter.write(" * Generated file.");
		outputFileBufferedWriter.newLine();

		if (this.dumpAnnotations) { 
			outputFileBufferedWriter.write(" * Warning: annotations are serialized here without their arguments (if any),");
			outputFileBufferedWriter.newLine();
			outputFileBufferedWriter.write(" * except for @Flatten that we skip.");
			outputFileBufferedWriter.newLine();
		}

		outputFileBufferedWriter.write(" */");
		outputFileBufferedWriter.newLine();

		outputFileBufferedWriter.newLine();

		writeAnnotations(rootDefinition, false);

		outputFileBufferedWriter.write("composite " + rootDefinition.getName() + "_flat {");
		outputFileBufferedWriter.newLine();
	}

	public void writeFooter() throws IOException {
		outputFileBufferedWriter.write("}");
		outputFileBufferedWriter.newLine();
	}

	private void run(final Definition definition, Map<Object, Object> context) throws IOException {

		assert ASTHelper.isComposite(definition);

		final Interface[] interfaces = ((InterfaceContainer) definition)
				.getInterfaces();

		if (interfaces != null) {
			outputFileBufferedWriter.write(tab + "// "+ interfaces.length + " Interface(s)");
			outputFileBufferedWriter.newLine();

			for (int i = 0; i < interfaces.length; i++) {
				final MindInterface itf = (MindInterface) interfaces[i];

				writeAnnotations(itf, true);

				outputFileBufferedWriter.write(tab
						+ (itf.getRole() ==  TypeInterface.SERVER_ROLE ? "provides " : "requires ")
						+ itf.getSignature() + " "
						+ (itf.getContingency() != null ? itf.getContingency() : "")
						+ "as " + itf.getName());
				if (itf.getNumberOfElement() != null) {
					outputFileBufferedWriter.write("[" + itf.getNumberOfElement() + "]");
				}
				outputFileBufferedWriter.write(semicolon);
				outputFileBufferedWriter.newLine();
			}
			outputFileBufferedWriter.newLine();
		}

		// sub-components
		final Component[] subComponents = ((ComponentContainer) definition)
				.getComponents();

		// TODO: Handle anonymous components writing

		if (subComponents != null) {
			outputFileBufferedWriter.write(tab + "// "+ subComponents.length + " Subcomponent(s)");
			outputFileBufferedWriter.newLine();

			for (int i = 0; i < subComponents.length; i++) {
				try {
					Component subComp = subComponents[i];
					Definition subCompDef = ASTHelper.getResolvedComponentDefinition(subComp, loaderItf, context);

					/*
					if (subComponents[i] instanceof AnonymousDefinitionContainer) {
						outputFileBufferedWriter.write(tab + "contains as " + subComponents[i].getName());
						outputFileBufferedWriter.newLine();
						writeAnnotations(subComponents[i], true);

						assert ASTHelper.isPrimitive(subCompDef); // should be since all the GarbageComposite handling has been done
						if (ASTHelper.isComposite(subCompDef)) {
							logger.warning("Anonymous sub-component " + subComponents[i] + " should have been handled by garbage composite !! Skip !");
							continue;
						}

						outputFileBufferedWriter.write(tab + "primitive {");
						outputFileBufferedWriter.newLine();

						// TODO: serialize primitive component body

						outputFileBufferedWriter.newLine();
						outputFileBufferedWriter.write(tab + "};");
					} else { */
					writeAnnotations(subComp, true);
					outputFileBufferedWriter.write(tab + "contains " + subCompDef.getName());

					// -----------------
					// Handle Arguments
					// -----------------

					// For this we need to obtain the current DefinitionReference matching the sub-component
					// instance, and the according Arguments.
					DefinitionReference subCompDefRef = subComp.getDefinitionReference();

					if (subCompDefRef instanceof ArgumentContainer) {

						Argument[] currSubCompArgs = ((ArgumentContainer) subCompDefRef).getArguments();

						if (currSubCompArgs.length > 0) {

							outputFileBufferedWriter.write("(");

							for (int j = 0 ; j < currSubCompArgs.length ; j++) {
								outputFileBufferedWriter.write(currSubCompArgs[j].getName() + " = ");

								// Different kinds of Value-s can be encountered
								outputFileBufferedWriter.write(getValueString(currSubCompArgs[j].getValue()));

								if (j < currSubCompArgs.length - 1)
									outputFileBufferedWriter.write(", ");
							}

							outputFileBufferedWriter.write(")");
						}
					}

					outputFileBufferedWriter.write(" as " + subComponents[i].getName() + semicolon);
					/*}*/
				} catch (ADLException e) {
					logger.warning("Could not serialize sub-component " + subComponents[i].getName() + " since its definition could not be resolved.");
					continue;
				}
				outputFileBufferedWriter.newLine();
			}
			outputFileBufferedWriter.newLine();
		}

		// bindings
		final Binding[] bindings = ((BindingContainer) definition).getBindings();

		if (bindings != null) {
			if (bindings.length == 0)
				outputFileBufferedWriter.write(tab + "// No binding");
			else
				outputFileBufferedWriter.write(tab + "// "+ bindings.length + " Binding(s)");

			outputFileBufferedWriter.newLine();

			for (int i = 0; i < bindings.length; i++) {
				final Binding binding = bindings[i];

				writeAnnotations(binding, true);

				outputFileBufferedWriter.write(tab + "binds " + binding.getFromComponent() + "." + binding.getFromInterface());
				if (binding.getFromInterfaceNumber() != null) {
					outputFileBufferedWriter.write("[" + binding.getFromInterfaceNumber() + "]");
				}
				outputFileBufferedWriter.write(" to " + binding.getToComponent() + "." + binding.getToInterface());
				if (binding.getToInterfaceNumber() != null) {
					outputFileBufferedWriter.write("[" + binding.getToInterfaceNumber() + "]");
				}
				outputFileBufferedWriter.write(semicolon);
				outputFileBufferedWriter.newLine();
			}
		}
	}

	/**
	 * Serialize all annotations except GarbageComposite (since we already flattened...)
	 * This methods handles the annotation name from introspection, but we do NOT serialize
	 * the annotations values.
	 * 
	 * @throws IOException 
	 */
	private void writeAnnotations(Node container, boolean inBody) throws IOException {

		if (!dumpAnnotations) return;

		// serialize annotations
		Annotation[] arrayOfAnnotations = AnnotationHelper.getAnnotations(container);

		if (arrayOfAnnotations == null)
			return;

		for (Annotation currAnno : arrayOfAnnotations) {
			if (currAnno instanceof GarbageComposite || currAnno instanceof Flatten)
				continue;

			String line = "";
			if (inBody)
				line = line + tab;
			line = line + "@" + currAnno.getClass().getSimpleName();

			outputFileBufferedWriter.write(line);
			outputFileBufferedWriter.newLine();
		}
	}

	/**
	 * Mix of @see org.ow2.mind.doc.HTMLDocumentationHelper#getValueString and
	 * @see AttributeInstantiator#toValueString
	 * But here we don't want to add quotes "\"" "\"" at the beginning and
	 * the end of StringLiteral-s (do not modify content) 
	 * 
	 * @param value
	 * @return
	 */
	public String getValueString(final Value value) {
		String valueString = null;
		if(value != null) {

			if (value instanceof NullLiteral) {
				valueString = "NULL";
			} else if (value instanceof BooleanLiteral) {
				valueString = ((BooleanLiteral) value).getValue();
			} else if (value instanceof NumberLiteral) {
				valueString = ((NumberLiteral) value).getValue();
			} else if (value instanceof StringLiteral) {
				valueString = ((StringLiteral) value).getValue();
			} else if (value instanceof Reference) {
				valueString = ((Reference) value).getRef();
			} else if (value instanceof CompoundValue) {
				final StringBuilder sb = new StringBuilder();
				sb.append("{");
				final CompoundValueField[] fields = ((CompoundValue) value)
						.getCompoundValueFields();
				for (int i = 0; i < fields.length; i++) {
					final CompoundValueField field = fields[i];
					if (field.getName() != null) {
						sb.append(".").append(field.getName()).append("=");
					}
					sb.append(getValueString(field.getValue()));
					if (i < fields.length - 1) {
						sb.append(", ");
					}
				}
				sb.append("}");
				return sb.toString();

			}
		}
		return valueString;
	}

}
