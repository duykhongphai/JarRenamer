package main.jarrenamerapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.Cursor;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.controlsfx.control.StatusBar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import javafx.beans.value.ChangeListener;
import javafx.collections.transformation.FilteredList;

public class JarRenamerController {

    @FXML private TextField jarFileField;
    @FXML private TextArea mappingTextArea;
    @FXML private TextField mappingFileField;
    @FXML private ToggleGroup modeGroup;
    @FXML private RadioButton mappingRadio;
    @FXML private RadioButton textMappingRadio;
    @FXML private RadioButton prefixRadio;
    @FXML private RadioButton replaceRadio;
    @FXML private RadioButton threeFilesRadio;
    @FXML private CheckBox handleDuplicatesCheck;
    @FXML private TextField prefixField;
    @FXML private TextField textToReplaceField;
    @FXML private TextField replacementTextField;
    @FXML private Button executeButton;
    @FXML private ProgressBar progressBar;
    @FXML private StatusBar statusBar;
    @FXML private HBox prefixBox;
    @FXML private VBox replaceBox;
    @FXML private HBox mappingFileBox;
    @FXML private VBox mappingTextBox;
    @FXML private VBox threeFilesBox;
    @FXML private TextField classNamesFileField;
    @FXML private TextField methodNamesFileField;
    @FXML private TextField fieldNamesFileField;
    @FXML private TitledPane classesPane;
    @FXML private TreeView<String> classTreeView;
    @FXML private TextField searchField;
    @FXML private Button selectAllButton;
    @FXML private Button deselectAllButton;

    private ExecutorService executorService;
    private CheckBoxTreeItem<String> rootItem;
    private final Map<String, CheckBoxTreeItem<String>> packageItems = new HashMap<>();
    private final Set<String> selectedClasses = new HashSet<>();
    private final Map<String, CheckBoxTreeItem<String>> classItems = new HashMap<>();

    @FXML
    public void initialize() {
        executorService = Executors.newSingleThreadExecutor();
        mappingRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) updateUIForMode("mapping");
        });

        textMappingRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) updateUIForMode("textMapping");
        });

        prefixRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) updateUIForMode("prefix");
        });

        replaceRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) updateUIForMode("replace");
        });

        threeFilesRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) updateUIForMode("threeFiles");
        });

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterTreeView(newValue);
        });

        progressBar.setVisible(false);
        statusBar.setText("Ready");
        updateUIForMode("mapping");
    }

    private void filterTreeView(String searchText) {
        if (rootItem == null) return;

        if (searchText == null || searchText.trim().isEmpty()) {
            showAllItems(rootItem);
        } else {
            String searchLower = searchText.toLowerCase();
            filterItems(rootItem, searchLower);
        }
    }

    private boolean filterItems(CheckBoxTreeItem<String> item, String searchText) {
        if (item == null) return false;

        boolean matches = item.getValue().toLowerCase().contains(searchText);
        boolean childMatches = false;
        if (!item.isLeaf()) {
            for (TreeItem<String> child : new ArrayList<>(item.getChildren())) {
                if (child instanceof CheckBoxTreeItem) {
                    boolean match = filterItems((CheckBoxTreeItem<String>) child, searchText);
                    childMatches |= match;
                }
            }
        }
        boolean visible = matches || childMatches;
        item.setExpanded(visible);
        return visible;
    }

    private void showAllItems(CheckBoxTreeItem<String> item) {
        if (item == null) return;
        item.setExpanded(true);
        if (!item.isLeaf()) {
            for (TreeItem<String> child : item.getChildren()) {
                if (child instanceof CheckBoxTreeItem) {
                    showAllItems((CheckBoxTreeItem<String>) child);
                }
            }
        }
    }

    private void updateUIForMode(String mode) {
        prefixBox.setVisible(false);
        prefixBox.setManaged(false);
        replaceBox.setVisible(false);
        replaceBox.setManaged(false);
        mappingFileBox.setVisible(false);
        mappingFileBox.setManaged(false);
        mappingTextBox.setVisible(false);
        mappingTextBox.setManaged(false);
        threeFilesBox.setVisible(false);
        threeFilesBox.setManaged(false);

        switch (mode) {
            case "mapping":
                mappingFileBox.setVisible(true);
                mappingFileBox.setManaged(true);
                break;
            case "textMapping":
                mappingTextBox.setVisible(true);
                mappingTextBox.setManaged(true);
                break;
            case "prefix":
                prefixBox.setVisible(true);
                prefixBox.setManaged(true);
                break;
            case "replace":
                replaceBox.setVisible(true);
                replaceBox.setManaged(true);
                break;
            case "threeFiles":
                threeFilesBox.setVisible(true);
                threeFilesBox.setManaged(true);
                break;
        }
    }

    @FXML
    protected void onSelectJarButtonClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select JAR File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
        File selectedFile = fileChooser.showOpenDialog(jarFileField.getScene().getWindow());
        if (selectedFile != null) {
            jarFileField.setText(selectedFile.getAbsolutePath());
            loadClassesFromJar(selectedFile);
        }
    }

    @FXML
    protected void onSelectClassNamesFileClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Class Names File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(classNamesFileField.getScene().getWindow());
        if (selectedFile != null) {
            classNamesFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    protected void onSelectMethodNamesFileClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Method Names File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(methodNamesFileField.getScene().getWindow());
        if (selectedFile != null) {
            methodNamesFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    protected void onSelectFieldNamesFileClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Field Names File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(fieldNamesFileField.getScene().getWindow());
        if (selectedFile != null) {
            fieldNamesFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void loadClassesFromJar(File jarFile) {
        if (classTreeView != null) {
            classTreeView.setCursor(Cursor.WAIT);
        }
        statusBar.setText("Loading classes from JAR...");
        executorService.submit(() -> {
            try {
                rootItem = new CheckBoxTreeItem<>("All Classes");
                rootItem.setExpanded(true);
                packageItems.clear();
                selectedClasses.clear();
                classItems.clear();
                Set<String> classNames = new TreeSet<>();
                try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile))) {
                    JarEntry entry;
                    while ((entry = jarIn.getNextJarEntry()) != null) {
                        String entryName = entry.getName();
                        if (entryName.endsWith(".class")) {
                            String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                            classNames.add(className);
                        }
                    }
                }
                for (String className : classNames) {
                    addClassToTree(className);
                }
                final Set<String> finalClassNames = classNames;
                Platform.runLater(() -> {
                    classTreeView.setRoot(rootItem);
                    classTreeView.setCellFactory(tv -> new CheckBoxTreeCell<>());
                    rootItem.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        updateSelectedClasses(rootItem, newVal);
                    });
                    rootItem.setSelected(true);
                    classesPane.setVisible(true);
                    classesPane.setManaged(true);
                    statusBar.setText("Loaded " + finalClassNames.size() + " classes from JAR.");
                    classTreeView.setCursor(Cursor.DEFAULT);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusBar.setText("Error loading classes: " + e.getMessage());
                    if (classTreeView != null) {
                        classTreeView.setCursor(Cursor.DEFAULT);
                    }
                });
            }
        });
    }

    private void addClassToTree(String className) {
        int lastDot = className.lastIndexOf('.');
        String packageName = (lastDot > 0) ? className.substring(0, lastDot) : "";
        String simpleClassName = (lastDot > 0) ? className.substring(lastDot + 1) : className;
        CheckBoxTreeItem<String> parentItem = rootItem;

        if (!packageName.isEmpty()) {
            String[] packages = packageName.split("\\.");
            String currentPackage = "";
            for (String pkg : packages) {
                currentPackage = currentPackage.isEmpty() ? pkg : currentPackage + "." + pkg;
                CheckBoxTreeItem<String> packageItem = packageItems.get(currentPackage);
                if (packageItem == null) {
                    packageItem = new CheckBoxTreeItem<>(pkg);
                    packageItem.setExpanded(true);
                    CheckBoxTreeItem<String> finalPackageItem = packageItem;
                    packageItem.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        updateSelectedClasses(finalPackageItem, newVal);
                    });

                    packageItems.put(currentPackage, packageItem);
                    parentItem.getChildren().add(packageItem);
                    parentItem = packageItem;
                } else {
                    parentItem = packageItem;
                }
            }
        }
        CheckBoxTreeItem<String> classItem = new CheckBoxTreeItem<>(simpleClassName);
        classItem.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                selectedClasses.add(className);
            } else {
                selectedClasses.remove(className);
            }
        });
        parentItem.getChildren().add(classItem);
        classItems.put(className, classItem);
        selectedClasses.add(className);
    }

    private void updateSelectedClasses(CheckBoxTreeItem<String> item, boolean selected) {
        if (item.getChildren().isEmpty()) {
            String className = getFullClassName(item);
            if (selected) {
                selectedClasses.add(className);
            } else {
                selectedClasses.remove(className);
            }
        } else {
            for (TreeItem<String> child : item.getChildren()) {
                if (child instanceof CheckBoxTreeItem) {
                    ((CheckBoxTreeItem<String>) child).setSelected(selected);
                }
            }
        }
    }

    private String getFullClassName(TreeItem<String> item) {
        if (item == rootItem) return "";

        List<String> path = new ArrayList<>();
        TreeItem<String> current = item;

        while (current != null && current != rootItem) {
            path.add(0, current.getValue());
            current = current.getParent();
        }

        return String.join(".", path);
    }

    @FXML
    protected void onSelectAllButtonClick() {
        if (rootItem != null) {
            rootItem.setSelected(true);
        }
    }

    @FXML
    protected void onDeselectAllButtonClick() {
        if (rootItem != null) {
            rootItem.setSelected(false);
        }
    }

    @FXML
    protected void onSelectMappingButtonClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Mapping File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Mapping Files", "*.txt", "*.map", "*.mapping"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(mappingFileField.getScene().getWindow());
        if (selectedFile != null) {
            mappingFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    private boolean validateInputs() {
        StringBuilder errorMessage = new StringBuilder();
        if (jarFileField.getText().isEmpty()) {
            errorMessage.append("JAR file is required\n");
        }
        if (mappingRadio.isSelected()) {
            if (mappingFileField.getText().isEmpty()) {
                errorMessage.append("Mapping file or URL is required\n");
            }
        } else if (textMappingRadio.isSelected()) {
            if (mappingTextArea.getText().isEmpty()) {
                errorMessage.append("Mapping content is required\n");
            }
        } else if (prefixRadio.isSelected()) {
            if (prefixField.getText().isEmpty()) {
                errorMessage.append("Prefix is required\n");
            }
        } else if (replaceRadio.isSelected()) {
            if (textToReplaceField.getText().isEmpty()) {
                errorMessage.append("Text to replace is required\n");
            }
        } else if (threeFilesRadio.isSelected()) {
            if (classNamesFileField.getText().isEmpty()) {
                errorMessage.append("Class names file is required\n");
            }
            if (methodNamesFileField.getText().isEmpty()) {
                errorMessage.append("Method names file is required\n");
            }
            if (fieldNamesFileField.getText().isEmpty()) {
                errorMessage.append("Field names file is required\n");
            }
        }

        if (!errorMessage.isEmpty()) {
            statusBar.setText(errorMessage.toString().trim());
            return false;
        }

        if (selectedClasses.isEmpty() && classesPane.isVisible()) {
            statusBar.setText("No classes selected for renaming");
            return false;
        }

        return true;
    }

    @FXML
    protected void onExecuteButtonClick() {
        if (!validateInputs()) {
            return;
        }
        executeButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusBar.setText("Processing JAR file...");

        executorService.submit(() -> {
            try {
                File jarFile = new File(jarFileField.getText());

                JarRenamerService renamerService;
                boolean handleDuplicates = handleDuplicatesCheck.isSelected();
                Set<String> classesToRename = classesPane.isVisible() ? new HashSet<>(selectedClasses) : null;

                if (threeFilesRadio.isSelected()) {
                    List<String> classNames = readLinesFromFile(new File(classNamesFileField.getText()));
                    List<String> methodNames = readLinesFromFile(new File(methodNamesFileField.getText()));
                    List<String> fieldNames = readLinesFromFile(new File(fieldNamesFileField.getText()));

                    renamerService = new JarRenamerService(
                            jarFile,
                            classNames,
                            methodNames,
                            fieldNames,
                            handleDuplicates,
                            classesToRename
                    );
                } else if (prefixRadio.isSelected()) {
                    String prefix = prefixField.getText();
                    renamerService = new JarRenamerService(
                            jarFile,
                            new HashMap<>(),
                            true,
                            prefix,
                            false,
                            null,
                            null,
                            handleDuplicates,
                            classesToRename
                    );
                } else if (textMappingRadio.isSelected()) {
                    String mappingContent = mappingTextArea.getText();
                    renamerService = new JarRenamerService(
                            jarFile,
                            mappingContent,
                            true,
                            classesToRename
                    );
                } else if (replaceRadio.isSelected()) {
                    String textToReplace = textToReplaceField.getText();
                    String replacementText = replacementTextField.getText();
                    renamerService = new JarRenamerService(
                            jarFile,
                            new HashMap<>(),
                            false,
                            null,
                            true,
                            textToReplace,
                            replacementText,
                            handleDuplicates,
                            classesToRename
                    );
                } else {
                    String mappingPath = mappingFileField.getText();
                    if (mappingPath.startsWith("http://") || mappingPath.startsWith("https://")) {
                        renamerService = new JarRenamerService(
                                jarFile,
                                mappingPath,
                                false,
                                classesToRename
                        );
                    } else {
                        File mappingFile = new File(mappingPath);
                        renamerService = new JarRenamerService(
                                jarFile,
                                mappingFile,
                                classesToRename
                        );
                    }
                }

                File outputJar = renamerService.execute();
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    statusBar.setText("Renaming completed successfully. Output: " + outputJar.getAbsolutePath());
                    executeButton.setDisable(false);
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Renaming Completed");
                    alert.setHeaderText("JAR Renaming Completed Successfully");
                    alert.setContentText("Output JAR file: " + outputJar.getAbsolutePath());
                    alert.showAndWait();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    statusBar.setText("Error: " + e.getMessage());
                    executeButton.setDisable(false);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Renaming Error");
                    alert.setHeaderText("An error occurred during JAR renaming");
                    alert.setContentText(e.getMessage());
                    TextArea exceptionTextArea = new TextArea();
                    StringBuilder sb = new StringBuilder();
                    sb.append(e.toString()).append("\n");
                    for (StackTraceElement element : e.getStackTrace()) {
                        sb.append("  at ").append(element).append("\n");
                    }
                    exceptionTextArea.setText(sb.toString());
                    exceptionTextArea.setEditable(false);

                    alert.getDialogPane().setExpandableContent(exceptionTextArea);
                    alert.showAndWait();
                });
            }
        });
    }

    private List<String> readLinesFromFile(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        List<String> nonEmptyLines = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                nonEmptyLines.add(line);
            }
        }

        return nonEmptyLines;
    }

    public void onClose() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}