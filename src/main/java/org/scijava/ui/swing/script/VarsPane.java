/*
 * #%L
 * Script Editor and Interpreter for SciJava script languages.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Max Planck Institute of Molecular Cell Biology and Genetics,
 * and others.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.ui.swing.script;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import org.scijava.Context;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptREPL;
import org.scijava.script.ScriptService;

/**
 * A side pane with information about available languages and variables.
 *
 * @author Curtis Rueden
 */
public class VarsPane extends JPanel {

	private final ScriptREPL repl;
	private final JComboBox langBox;
	private final VarsTableModel varsTableModel;

	public VarsPane(final Context context, final ScriptREPL repl) {
		this.repl = repl;

		setLayout(new BorderLayout());

		final ScriptService scriptService = context.service(ScriptService.class);
		final List<ScriptLanguage> langList = scriptService.getLanguages();
		final ScriptLanguage[] langs = langList.toArray(new ScriptLanguage[0]);
		langBox = new JComboBox(langs);
		langBox.setMaximumRowCount(25);
		langBox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				final ScriptLanguage lang = (ScriptLanguage) langBox.getSelectedItem();
				if (lang == repl.getInterpreter().getLanguage()) return; // no change
				try {
					repl.lang(lang.getLanguageName());
				}
				catch (final RuntimeException exc) {
					// Something went wrong...
					// TODO: Issue the exception to the log via the LogService.
				}
				update();
			}
		});
		add(langBox, BorderLayout.NORTH);

		varsTableModel = new VarsTableModel();
		final JTable varsTable = new JTable(varsTableModel);
		varsTable.getColumnModel().getColumn(0).setMinWidth(120);
		varsTable.getColumnModel().getColumn(0).setMaxWidth(120);
		varsTable.getColumnModel().getColumn(1).setPreferredWidth(120);
		add(new JScrollPane(varsTable), BorderLayout.CENTER);
		varsTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);

		final JCheckBox showTypes = new JCheckBox("Show variable types");
		showTypes.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				varsTableModel.setShowTypes(showTypes.isSelected());
			}

		});
		add(showTypes, BorderLayout.SOUTH);

		update();
	}

	// -- VarsPane methods --

	public void update() {
		langBox.setSelectedItem(repl.getInterpreter().getLanguage());
		varsTableModel.update();
	}

	// -- Helper classes --

	/** Helper class for the table of variables. */
	private class VarsTableModel extends AbstractTableModel {

		private final ArrayList<String> varNames = new ArrayList<String>();

		private boolean showTypes;

		// -- InterpreterTableModel methods --

		public void update() {
			varNames.clear();
			try {
				varNames.addAll(repl.getInterpreter().getBindings().keySet());
				Collections.sort(varNames);
			}
			catch (final RuntimeException exc) {
				// Something went wrong. Leave the variables list empty.
				// TODO: Issue the exception to the log via the LogService.
			}
			fireTableDataChanged();
		}

		public void setShowTypes(final boolean showTypes) {
			this.showTypes = showTypes;
			VarsPane.this.update();
		}

		// -- TableModel methods --

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public String getColumnName(final int columnIndex) {
			switch (columnIndex) {
				case 0:
					return "Name";
				case 1:
					return "Value";
				default:
					throw invalidColumnException(columnIndex);
			}
		}

		@Override
		public int getRowCount() {
			return repl.getInterpreter().getBindings().size();
		}

		@Override
		public Object getValueAt(final int rowIndex, final int columnIndex) {
			if (rowIndex >= varNames.size()) return null;
			final String varName = varNames.get(rowIndex);
			switch (columnIndex) {
				case 0:
					return varName;
				case 1:
					return value(varName);
				default:
					throw invalidColumnException(columnIndex);
			}
		}

		// -- Helper methods --

		private String value(final String varName) {
			final Object value = repl.getInterpreter().getBindings().get(varName);
			if (value == null) return "<null>";
			final String vs = value.toString();
			final String type = value.getClass().getName();
			return vs.startsWith(type) || !showTypes ? vs : vs + " [" + type + "]";
		}

		private ArrayIndexOutOfBoundsException invalidColumnException(
			final int columnIndex)
		{
			return new ArrayIndexOutOfBoundsException("Invalid column index: " +
				columnIndex);
		}

	}

}
