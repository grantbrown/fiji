package fiji.updater.ui;

import fiji.updater.logic.PluginCollection;

import fiji.updater.logic.PluginCollection.UpdateSite;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

public class SitesDialog extends JDialog implements ActionListener, ItemListener {
	protected UpdaterFrame updaterFrame;
	protected PluginCollection plugins;
	protected List<String> names;

	protected DataModel tableModel;
	protected JTable table;
	protected JButton add, edit, remove, close;
	protected JCheckBox forUpload;

	public SitesDialog(UpdaterFrame owner, PluginCollection plugins, boolean forUpload) {
		super(owner, "Manage update sites");
		updaterFrame = owner;
		this.plugins = plugins;
		names = new ArrayList<String>(plugins.getUpdateSiteNames());
		names.set(0, "Fiji");

		Container contentPane = getContentPane();
		contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));

		this.forUpload = new JCheckBox("For Uploading", forUpload);
		this.forUpload.addItemListener(this);

		tableModel = new DataModel();
		table = new JTable(tableModel) {
			public void valueChanged(ListSelectionEvent e) {
				super.valueChanged(e);
				edit.setEnabled(getSelectedRow() > 0);
				remove.setEnabled(getSelectedRow() > 0);
			}
		};
		table.setColumnSelectionAllowed(false);
		table.setRowSelectionAllowed(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableModel.setColumnWidths();
		JScrollPane scrollpane = new JScrollPane(table);
		scrollpane.setPreferredSize(new Dimension(tableModel.tableWidth, 100));
		contentPane.add(scrollpane);

		JPanel buttons = new JPanel();
		buttons.add(this.forUpload);
		add = SwingTools.button("Add", "Add", this, buttons);
		edit = SwingTools.button("Edit", "Edit", this, buttons);
		edit.setEnabled(false);
		remove = SwingTools.button("Remove", "Remove", this, buttons);
		remove.setEnabled(false);
		close = SwingTools.button("Close", "Close", this, buttons);
		contentPane.add(buttons);

		getRootPane().setDefaultButton(close);
		escapeCancels(this);
		pack();
		add.requestFocusInWindow();
		setLocationRelativeTo(owner);
	}

	protected UpdateSite getUpdateSite(String name) {
		return plugins.getUpdateSite(name.equals("Fiji") ? "" : name);
	}

	protected void add() {
		SiteDialog dialog = new SiteDialog();
		dialog.setVisible(true);
	}

	protected void edit(int row) {
		String name = names.get(row);
		UpdateSite updateSite = getUpdateSite(name);
		SiteDialog dialog = new SiteDialog(name, updateSite.url, updateSite.sshHost, updateSite.uploadDirectory, row);
		dialog.setVisible(true);
	}

	protected void delete(int row) {
		String name = names.get(row);
		plugins.removeUpdateSite(name);
		names.remove(row);
		tableModel.rowsChanged(row, names.size());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source == add)
			add();
		else if (source == edit)
			edit(table.getSelectedRow());
		else if (source == remove)
			delete(table.getSelectedRow());
		else if (source == close) {
			dispose();
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		tableModel.fireTableStructureChanged();
		tableModel.setColumnWidths();
	}

	protected class DataModel extends AbstractTableModel {
		protected int tableWidth;
		protected int[] widths = { 100, 300, 150, 150 };
		protected String[] headers = { "Name", "URL", "SSH Host", "Directory on Host" };

		public void setColumnWidths() {
			TableColumnModel columnModel = table.getColumnModel();
			for (int i = 0; i < tableModel.widths.length && i < getColumnCount(); i++) {
				TableColumn column = columnModel.getColumn(i);
				column.setPreferredWidth(tableModel.widths[i]);
				column.setMinWidth(tableModel.widths[i]);
				tableWidth += tableModel.widths[i];
			}
		}

		@Override
		public int getColumnCount() {
			return forUpload.isSelected() ? 4 : 2;
		}

		@Override
		public String getColumnName(int column) {
			return headers[column];
		}

		@Override
		public int getRowCount() {
			return names.size();
		}

		@Override
		public Object getValueAt(int row, int col) {
			String name = names.get(row);
			if (col == 0)
				return name;
			UpdateSite site = getUpdateSite(name);
			if (col == 1)
				return site.url;
			if (col == 2)
				return site.sshHost;
			if (col == 3)
				return site.uploadDirectory;
			return null;
		}

		public void rowChanged(int row) {
			rowsChanged(row, row + 1);
		}

		public void rowsChanged() {
			rowsChanged(0, names.size());
		}

		public void rowsChanged(int firstRow, int lastRow) {
			//fireTableChanged(new TableModelEvent(this, firstRow, lastRow));
			fireTableChanged(new TableModelEvent(this));
		}
	}

	protected class SiteDialog extends JDialog implements ActionListener {
		protected int row;
		protected JTextField name, url, sshHost, uploadDirectory;
		protected JButton ok, cancel;

		public SiteDialog() {
			this("", "", "", "", -1);
		}

		public SiteDialog(String name, String url, String sshHost, String uploadDirectory, int row) {
			super(SitesDialog.this, "Add update site");
			this.row = row;

			setPreferredSize(new Dimension(400, 150));
			Container contentPane = getContentPane();
			contentPane.setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridwidth = c.gridheight = 1;
			c.weightx = c.weighty = 0;
			c.gridx = c.gridy = 0;

			this.name = new JTextField(name, tableModel.widths[0]);
			this.url = new JTextField(url, tableModel.widths[1]);
			this.sshHost = new JTextField(sshHost, tableModel.widths[2]);
			this.uploadDirectory = new JTextField(uploadDirectory, tableModel.widths[3]);
			contentPane.add(new JLabel("Name:"), c);
			c.weightx = 1; c.gridx++;
			contentPane.add(this.name, c);
			c.weightx = 0; c.gridx = 0; c.gridy++;
			contentPane.add(new JLabel("URL:"), c);
			c.weightx = 1; c.gridx++;
			contentPane.add(this.url, c);
			if (forUpload.isSelected()) {
				c.weightx = 0; c.gridx = 0; c.gridy++;
				contentPane.add(new JLabel("SSH host:"), c);
				c.weightx = 1; c.gridx++;
				contentPane.add(this.sshHost, c);
				c.weightx = 0; c.gridx = 0; c.gridy++;
				contentPane.add(new JLabel("Upload directory:"), c);
				c.weightx = 1; c.gridx++;
				contentPane.add(this.uploadDirectory, c);
			}
			JPanel buttons = new JPanel();
			ok = new JButton("OK");
			ok.addActionListener(this);
			buttons.add(ok);
			cancel = new JButton("Cancel");
			cancel.addActionListener(this);
			buttons.add(cancel);
			c.weightx = 0; c.gridx = 0; c.gridwidth = 2; c.gridy++;
			contentPane.add(buttons, c);

			getRootPane().setDefaultButton(ok);
			pack();
			escapeCancels(this);
			setLocationRelativeTo(SitesDialog.this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Object source = e.getSource();
			if (source == ok) {
				if (row < 0) {
					if (names.contains(name.getText())) {
						error("Site '" + name.getText() + "' exists already!");
						return;
					}
					row = names.size();
					plugins.addUpdateSite(name.getText(), url.getText(), sshHost.getText(), uploadDirectory.getText(), 0l);
					names.add(name.getText());
				}
				else {
					String originalName = names.get(row);
					UpdateSite updateSite = getUpdateSite(originalName);
					String name = this.name.getText();
					boolean nameChanged = !name.equals(originalName);
					if (nameChanged) {
						if (names.contains(name)) {
							error("Site '" + name + "' exists already!");
							return;
						}
						plugins.renameUpdateSite(originalName, name);
						names.set(row, name);
					}
					updateSite.url = url.getText();
					updateSite.sshHost = sshHost.getText();
					updateSite.uploadDirectory = uploadDirectory.getText();
				}
				tableModel.rowChanged(row);
				table.setRowSelectionInterval(row, row);
			}
			dispose();
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		updaterFrame.updatePluginsTable();
	}

	public void error(String message) {
		SwingTools.showMessageBox(updaterFrame != null && updaterFrame.hidden,
			this, message, JOptionPane.ERROR_MESSAGE);
	}

	public static void escapeCancels(final JDialog dialog) {
		dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "ESCAPE");
		dialog.getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				dialog.dispose();
			}
		});
	}

	public static void main(String[] args) {
		PluginCollection plugins = new PluginCollection();
		try {
			plugins.read();
		} catch (Exception e) { ij.IJ.handleException(e); }
		SitesDialog dialog = new SitesDialog(null, plugins, !false);
		dialog.setVisible(true);
	}
}
