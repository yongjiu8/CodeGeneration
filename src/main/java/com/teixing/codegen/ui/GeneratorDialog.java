package com.teixing.codegen.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.openapi.ui.ComboBox;
import com.teixing.codegen.generator.CodeGenerator;
import com.teixing.codegen.meta.AbstractDbMetadata;
import com.teixing.codegen.meta.DbMetadata;
import com.teixing.codegen.meta.DbMetadataFactory;
import com.teixing.codegen.model.TableInfo;
import com.teixing.codegen.model.TableMapping;
import com.teixing.codegen.persistent.GeneratorSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 代码生成器主对话框
 */
public class GeneratorDialog extends DialogWrapper {

    private static final String TEMPLATE_CLASSIC = "iBatis2经典模板";
    private static final String TEMPLATE_MYBATIS = "MyBatis模板";

    private final Project project;
    private DbMetadata metadata;
    private boolean comboLoading; // 程序化刷新下拉框时屏蔽事件

    private final ComboBox<String> scenarioCombo = new ComboBox<>(new String[]{"单表增删改查"});
    private final ComboBox<String> templateCombo = new ComboBox<>(new String[]{TEMPLATE_CLASSIC, TEMPLATE_MYBATIS});
    private final ComboBox<String> moduleCombo = new ComboBox<>();
    private Module[] modules = new Module[0];

    private final ComboBox<String> dbTypeCombo = new ComboBox<>(DbMetadataFactory.getDbTypeLabels());
    private final JBTextField urlField = new JBTextField();
    private final JBTextField userField = new JBTextField();
    private final JBPasswordField pwdField = new JBPasswordField();
    private final ComboBox<String> schemaCombo = new ComboBox<>();
    private final JButton connectBtn = new JButton("连接");

    private final JBTextField searchField = new JBTextField();
    private final JCheckBox allCb = new JCheckBox("全选");
    private final TableModel tableModel = new TableModel();
    private final JBTable table = new JBTable(tableModel);

    private final JCheckBox domainCb = new JCheckBox("domain", true);
    private final JCheckBox sqlmapCb = new JCheckBox("sqlmap", true);
    private final JCheckBox serviceCb = new JCheckBox("service");
    private final JCheckBox regPageCb = new JCheckBox("注册页面");
    private final JCheckBox jspCb = new JCheckBox("jsp");
    private final JCheckBox vueCb = new JCheckBox("vue");
    private final JCheckBox jsCb = new JCheckBox("js");
    private final JCheckBox regBtnCb = new JCheckBox("注册按钮");

    private final JBTextField domainPathField = new JBTextField();

    public GeneratorDialog(Project project) {
        this(project, null);
    }

    /** @param presetOutputPath 预填的输出目录（右键包目录进入时带入） */
    public GeneratorDialog(Project project, @org.jetbrains.annotations.Nullable String presetOutputPath) {
        super(project);
        this.project = project;
        setTitle("数据库代码生成器");
        setOKButtonText("生成");
        init();
        initComponents();
        loadSettings();
        if (presetOutputPath != null && !presetOutputPath.isEmpty()) {
            domainPathField.setText(presetOutputPath.replace('\\', '/'));
        }
    }

    // ------------------------------------------------------------ UI 构建

    private void initComponents() {
        schemaCombo.setEditable(true);
        searchField.getEmptyText().setText("搜索表名");
        table.getEmptyText().setText("请先连接数据库并选择 schema");
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(240);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);

        // 暂未支持的生成项置灰
        for (JCheckBox cb : new JCheckBox[]{serviceCb, regPageCb, jspCb, vueCb, jsCb, regBtnCb}) {
            cb.setEnabled(false);
            cb.setToolTipText("当前版本暂不支持");
        }

        // 数据库类型切换 -> 有缓存地址则回填缓存，否则填充默认地址
        dbTypeCombo.addActionListener(e -> {
            if (comboLoading) {
                return;
            }
            String type = (String) dbTypeCombo.getSelectedItem();
            String cached = GeneratorSettings.getInstance(project).getState().urlCache.get(type);
            urlField.setText(cached != null && !cached.isEmpty() ? cached : templateOf(type));
        });

        connectBtn.addActionListener(e -> doConnect());

        schemaCombo.addActionListener(e -> {
            if (!comboLoading && metadata != null) {
                loadTables();
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                tableModel.setFilter(searchField.getText().trim());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                tableModel.setFilter(searchField.getText().trim());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                tableModel.setFilter(searchField.getText().trim());
            }
        });

        allCb.addActionListener(e -> tableModel.setAllSelected(allCb.isSelected()));

        // 模块列表
        modules = ModuleManager.getInstance(project).getModules();
        for (Module m : modules) {
            moduleCombo.addItem(m.getName());
        }
        moduleCombo.addActionListener(e -> {
            int idx = moduleCombo.getSelectedIndex();
            if (idx >= 0 && idx < modules.length && domainPathField.getText().trim().isEmpty()) {
                String root = firstSourceRoot(modules[idx]);
                if (root != null) {
                    domainPathField.setText(root);
                }
            }
        });
    }

    private static String templateOf(String label) {
        for (DbMetadataFactory.DbType t : DbMetadataFactory.DB_TYPES) {
            if (t.label.equals(label)) {
                return t.urlTemplate;
            }
        }
        return "";
    }

    private static String firstSourceRoot(Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
        for (VirtualFile r : roots) {
            if (r.getPath().contains("main/java")) {
                return r.getPath();
            }
        }
        return roots.length > 0 ? roots[0].getPath() : null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 第一行：开发场景 / 生成模板
        panel.add(new JBLabel("开发场景:"), gc(0, 0, 0));
        panel.add(scenarioCombo, gc(1, 0, 1));
        panel.add(new JBLabel("生成模板:"), gc(2, 0, 0));
        panel.add(templateCombo, gc(3, 0, 1));

        // 第二行：选择模块
        panel.add(new JBLabel("选择模块:"), gc(0, 1, 0));
        panel.add(moduleCombo, gcw(1, 1, 3, 1));

        // 数据库连接配置区
        JPanel dbPanel = buildDbPanel();
        GridBagConstraints c = gcw(0, 2, 4, 1);
        c.weighty = 0;
        panel.add(dbPanel, c);

        // 数据表搜索行
        panel.add(new JBLabel("数据表:"), gc(0, 3, 0));
        panel.add(searchField, gc(1, 3, 1));
        panel.add(allCb, gc(2, 3, 0));

        // 表列表
        GridBagConstraints tc = gcw(0, 4, 4, 1);
        tc.weighty = 1;
        tc.fill = GridBagConstraints.BOTH;
        JBScrollPane scrollPane = new JBScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(820, 260));
        panel.add(scrollPane, tc);

        // 生成内容复选框
        JPanel cbPanel = new JPanel(new GridLayout(2, 4, 12, 2));
        cbPanel.add(domainCb);
        cbPanel.add(sqlmapCb);
        cbPanel.add(serviceCb);
        cbPanel.add(regPageCb);
        cbPanel.add(jspCb);
        cbPanel.add(vueCb);
        cbPanel.add(jsCb);
        cbPanel.add(regBtnCb);
        GridBagConstraints cc = gcw(0, 5, 4, 1);
        panel.add(cbPanel, cc);

        // 说明
        JBLabel note = new JBLabel("注：选择模块目录（如 …/ph）后，实体生成到其下 domain 目录、sqlmap 生成到其下 sql 目录；TiDB 连接生成 .tidb.xml；已存在文件直接覆盖");
        note.setForeground(JBColor.GRAY);
        GridBagConstraints nc = gcw(0, 6, 4, 1);
        panel.add(note, nc);

        // domain 路径
        panel.add(new JBLabel("domain路径:"), gc(0, 7, 0));
        GridBagConstraints dc = gcw(1, 7, 2, 1);
        panel.add(domainPathField, dc);
        JButton browseBtn = new JButton("浏览");
        browseBtn.addActionListener(e -> doBrowse());
        panel.add(browseBtn, gc(3, 7, 0));

        panel.setPreferredSize(new Dimension(860, 720));
        return panel;
    }

    private JPanel buildDbPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("数据库连接配置"));

        p.add(new JBLabel("数据库类型:"), gc(0, 0, 0));
        p.add(dbTypeCombo, gcw(1, 0, 2, 1));

        p.add(new JBLabel("地址:"), gc(0, 1, 0));
        p.add(urlField, gcw(1, 1, 2, 1));

        p.add(new JBLabel("用户名:"), gc(0, 2, 0));
        p.add(userField, gcw(1, 2, 2, 1));

        p.add(new JBLabel("密码:"), gc(0, 3, 0));
        p.add(pwdField, gcw(1, 3, 2, 1));

        p.add(new JBLabel("数据库schema:"), gc(0, 4, 0));
        p.add(schemaCombo, gc(1, 4, 1));
        p.add(connectBtn, gc(2, 4, 0));

        return p;
    }

    private static GridBagConstraints gc(int x, int y, double weightx) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.weightx = weightx;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        return c;
    }

    private static GridBagConstraints gcw(int x, int y, int width, double weightx) {
        GridBagConstraints c = gc(x, y, weightx);
        c.gridwidth = width;
        return c;
    }

    // ------------------------------------------------------------ 事件处理

    private void doBrowse() {
        FileChooserDescriptor d = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        d.setTitle("选择 domain 输出目录");
        VirtualFile vf = FileChooser.chooseFile(d, project, null);
        if (vf != null) {
            domainPathField.setText(vf.getPath());
        }
    }

    private void doConnect() {
        String type = (String) dbTypeCombo.getSelectedItem();
        String url = urlField.getText().trim();
        String user = userField.getText().trim();
        String pwd = new String(pwdField.getPassword());
        if (url.isEmpty()) {
            Messages.showWarningDialog("请填写 JDBC 地址", "提示");
            return;
        }

        if (metadata != null) {
            metadata.close();
            metadata = null;
        }
        List<String> schemas = new ArrayList<>();
        Exception[] error = new Exception[1];
        ProgressManager.getInstance().run(new Task.Modal(project, "正在连接数据库", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    DbMetadata md = DbMetadataFactory.create(type, url, user, pwd);
                    md.connect();
                    schemas.addAll(md.getSchemas());
                    metadata = md;
                } catch (Exception ex) {
                    error[0] = ex;
                }
            }
        });
        if (error[0] != null) {
            Messages.showErrorDialog("连接失败：" + rootMsg(error[0]), "错误");
            return;
        }

        comboLoading = true;
        schemaCombo.removeAllItems();
        for (String s : schemas) {
            schemaCombo.addItem(s);
        }
        comboLoading = false;
        if (schemaCombo.getItemCount() > 0) {
            schemaCombo.setSelectedIndex(0);
            loadTables();
        } else {
            Messages.showInfoMessage("连接成功，但未查询到 schema，可手动输入后回车加载表", "提示");
        }
        saveSettings();
    }

    private void loadTables() {
        if (metadata == null) {
            return;
        }
        String schema = Objects.toString(schemaCombo.getSelectedItem(), "");
        List<TableInfo> tables = new ArrayList<>();
        Exception[] error = new Exception[1];
        ProgressManager.getInstance().run(new Task.Modal(project, "正在加载表列表", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    tables.addAll(metadata.getTables(schema));
                } catch (Exception ex) {
                    error[0] = ex;
                }
            }
        });
        if (error[0] != null) {
            Messages.showErrorDialog("加载表列表失败：" + rootMsg(error[0]), "错误");
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (TableInfo t : tables) {
            rows.add(new Row(t.getName(), AbstractDbMetadata.toClassName(t.getName()), t.getComment()));
        }
        tableModel.setRows(rows);
        allCb.setSelected(false);
    }

    @Override
    protected void doOKAction() {
        doGenerate();
    }

    private void doGenerate() {
        if (metadata == null) {
            Messages.showWarningDialog("请先连接数据库", "提示");
            return;
        }
        List<Row> selected = tableModel.selectedRows();
        if (selected.isEmpty()) {
            Messages.showWarningDialog("请勾选要生成的数据表", "提示");
            return;
        }
        if (!domainCb.isSelected() && !sqlmapCb.isSelected()) {
            Messages.showWarningDialog("请至少勾选 domain 或 sqlmap", "提示");
            return;
        }
        String domainPath = domainPathField.getText().trim().replace('\\', '/');
        if (domainPath.isEmpty()) {
            Messages.showWarningDialog("请选择输出路径", "提示");
            return;
        }
        Path[] dirs = outputDirs(domainPath);
        Path domainDir = dirs[0];
        Path xmlDir = dirs[1];
        String packageName = extractPackage(domainDir.toString());
        if (packageName == null) {
            Messages.showWarningDialog("无法从输出路径推导包名，请选择 src/main/java 下的目录", "提示");
            return;
        }

        String schema = Objects.toString(schemaCombo.getSelectedItem(), "");
        boolean mybatis = TEMPLATE_MYBATIS.equals(templateCombo.getSelectedItem());
        boolean tidb = "TiDB".equals(dbTypeCombo.getSelectedItem());
        boolean genDomain = domainCb.isSelected();
        boolean genSqlmap = sqlmapCb.isSelected();

        int[] count = {0};
        Exception[] error = new Exception[1];
        ProgressManager.getInstance().run(new Task.Modal(project, "正在生成代码", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    for (Row row : selected) {
                        indicator.setText("正在生成 " + row.tableName);
                        TableMapping tm = metadata.getTableMapping(schema, row.tableName,
                                packageName, row.className.trim(), row.cnName.trim());
                        if (genDomain) {
                            if (mybatis) {
                                CodeGenerator.generateDomain(tm, domainDir);
                            } else {
                                CodeGenerator.generateDomainClassic(tm, domainDir);
                            }
                            count[0]++;
                        }
                        if (genSqlmap) {
                            // 文件名沿用参考实现 mapTableTosqlFile：表名转小驼峰；TiDB 库生成 .tidb.xml
                            String fileBaseName = AbstractDbMetadata.toPropertyName(row.tableName);
                            if (mybatis) {
                                CodeGenerator.generateMybatisXml(tm, xmlDir, fileBaseName + (tidb ? ".tidb" : ""));
                            } else {
                                CodeGenerator.generateIBatisXml(tm, xmlDir, fileBaseName, tidb ? ".tidb" : "");
                            }
                            count[0]++;
                        }
                    }
                } catch (Exception ex) {
                    error[0] = ex;
                }
            }
        });
        if (error[0] != null) {
            Messages.showErrorDialog("生成失败：" + rootMsg(error[0]), "错误");
            return;
        }
        saveSettings();
        VirtualFileManager.getInstance().asyncRefresh(null);
        Messages.showInfoMessage("生成完成（已存在的文件已覆盖），共生成 " + count[0] + " 个文件\ndomain 目录："
                + domainDir + "\nsqlmap 目录：" + xmlDir, "成功");
    }

    // ------------------------------------------------------------ 工具方法

    /** 从 domain 路径推导包名；路径不在 src/main/java 下时返回 null */
    private static @Nullable String extractPackage(String domainPath) {
        String p = domainPath.replace('\\', '/');
        int idx = p.indexOf("src/main/java/");
        if (idx < 0) {
            return null;
        }
        String pkg = p.substring(idx + "src/main/java/".length());
        pkg = pkg.replace('/', '.');
        while (pkg.endsWith(".")) {
            pkg = pkg.substring(0, pkg.length() - 1);
        }
        return pkg.isEmpty() ? null : pkg;
    }

    /**
     * 输出目录推导：返回 [domain目录, sql目录]。
     * - 选择的路径以 /domain 结尾（如 .../ph/domain）：domain -> 该路径，sql -> 同级 sql 目录（.../ph/sql）
     * - 否则（如 .../ph）：domain -> 路径下 domain 目录（.../ph/domain），sql -> 路径下 sql 目录（.../ph/sql）
     */
    private static Path[] outputDirs(String domainPath) {
        String p = domainPath.replace('\\', '/');
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.endsWith("/domain")) {
            Path base = Path.of(p);
            Path parent = base.getParent() != null ? base.getParent() : base;
            return new Path[]{base, parent.resolve("sql")};
        }
        Path base = Path.of(p);
        return new Path[]{base.resolve("domain"), base.resolve("sql")};
    }

    private static String rootMsg(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) {
            r = r.getCause();
        }
        return r.getMessage() == null ? r.toString() : r.getMessage();
    }

    private void loadSettings() {
        GeneratorSettings.State s = GeneratorSettings.getInstance(project).getState();
        comboLoading = true;
        dbTypeCombo.setSelectedItem(s.dbType);
        comboLoading = false;
        urlField.setText(s.url);
        userField.setText(s.username);
        pwdField.setText(s.password);
        domainPathField.setText(s.domainPath);
        templateCombo.setSelectedItem(s.template);
        domainCb.setSelected(s.genDomain);
        sqlmapCb.setSelected(s.genSqlmap);
        if (!s.schema.isEmpty()) {
            schemaCombo.setSelectedItem(s.schema);
        }
    }

    private void saveSettings() {
        GeneratorSettings.State s = GeneratorSettings.getInstance(project).getState();
        s.dbType = Objects.toString(dbTypeCombo.getSelectedItem(), "TiDB");
        s.url = urlField.getText().trim();
        s.username = userField.getText().trim();
        s.password = new String(pwdField.getPassword());
        s.schema = Objects.toString(schemaCombo.getSelectedItem(), "");
        s.domainPath = domainPathField.getText().trim();
        s.template = Objects.toString(templateCombo.getSelectedItem(), TEMPLATE_CLASSIC);
        s.genDomain = domainCb.isSelected();
        s.genSqlmap = sqlmapCb.isSelected();
        s.urlCache.put(s.dbType, s.url);
    }

    @Override
    public void dispose() {
        if (metadata != null) {
            metadata.close();
        }
        super.dispose();
    }

    // ------------------------------------------------------------ 表格模型

    private static final class Row {
        boolean selected;
        final String tableName;
        String className;
        String cnName;

        Row(String tableName, String className, String cnName) {
            this.tableName = tableName;
            this.className = className;
            this.cnName = cnName;
        }
    }

    private static final class TableModel extends AbstractTableModel {
        private static final String[] COLS = {"选择", "表名", "画面英文名", "画面中文名"};

        private List<Row> all = new ArrayList<>();
        private List<Row> view = new ArrayList<>();
        private String filter = "";

        void setRows(List<Row> rows) {
            this.all = rows;
            applyFilter();
        }

        void setFilter(String filter) {
            this.filter = filter == null ? "" : filter.toLowerCase();
            applyFilter();
        }

        private void applyFilter() {
            if (filter.isEmpty()) {
                view = new ArrayList<>(all);
            } else {
                view = new ArrayList<>();
                for (Row r : all) {
                    if (r.tableName.toLowerCase().contains(filter)) {
                        view.add(r);
                    }
                }
            }
            fireTableDataChanged();
        }

        void setAllSelected(boolean selected) {
            for (Row r : view) {
                r.selected = selected;
            }
            fireTableDataChanged();
        }

        List<Row> selectedRows() {
            List<Row> list = new ArrayList<>();
            for (Row r : all) {
                if (r.selected) {
                    list.add(r);
                }
            }
            return list;
        }

        @Override
        public int getRowCount() {
            return view.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex != 1;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row r = view.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return r.selected;
                case 1:
                    return r.tableName;
                case 2:
                    return r.className;
                case 3:
                    return r.cnName;
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Row r = view.get(rowIndex);
            if (columnIndex == 0) {
                r.selected = Boolean.TRUE.equals(aValue);
            } else if (columnIndex == 2) {
                r.className = Objects.toString(aValue, "");
            } else if (columnIndex == 3) {
                r.cnName = Objects.toString(aValue, "");
            }
        }
    }
}
