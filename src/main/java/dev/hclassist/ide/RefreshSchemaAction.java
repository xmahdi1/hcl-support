package dev.hclassist.ide;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import dev.hclassist.core.ProviderSchema;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class RefreshSchemaAction extends AnAction {
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project == null) return;
        VirtualFile selected = event.getData(CommonDataKeys.VIRTUAL_FILE);
        String basePath = project.getBasePath();
        if (basePath == null) return;
        Path directory = selected == null ? Path.of(basePath) : Path.of(selected.isDirectory() ? selected.getPath() : selected.getParent().getPath());
        Properties properties = new Properties();
        Path settings = Path.of(basePath, ".hcl-assist.properties");
        if (Files.isRegularFile(settings)) try (Reader reader = Files.newBufferedReader(settings)) { properties.load(reader); }
        catch (IOException ignored) { }
        String executable = properties.getProperty("terraform.executable", "terraform");
        new Task.Backgroundable(project, "Refreshing HCL provider schemas", true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                try {
                    GeneralCommandLine command = new GeneralCommandLine(executable, "providers", "schema", "-json")
                            .withWorkDirectory(directory.toFile()).withCharset(StandardCharsets.UTF_8);
                    var handler = new CapturingProcessHandler(command);
                    var output = handler.runProcessWithProgressIndicator(indicator, 60_000);
                    if (indicator.isCanceled() || output.isCancelled()) return;
                    if (output.isTimeout()) throw new IOException("Provider schema command exceeded 60 seconds.");
                    if (output.getExitCode() != 0) throw new IOException(output.getStderr().strip());
                    ProviderSchema.read(new StringReader(output.getStdout()));
                    Path target = directory.resolve(".hcl-assist.providers.json");
                    Path temporary = Files.createTempFile(directory, ".hcl-schema-", ".json");
                    try {
                        Files.writeString(temporary, output.getStdout(), StandardCharsets.UTF_8);
                        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
                    } finally { Files.deleteIfExists(temporary); }
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (!project.isDisposed()) Messages.showInfoMessage(project, "Provider schema saved to " + target + ". Required-field templates and completion will use it on the next request.", "HCL Assist");
                    });
                } catch (Exception exception) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (!project.isDisposed()) Messages.showErrorDialog(project, "Could not refresh provider schemas in " + directory + ".\nInitialize this module first using terraform init or tofu init.\n\n" + exception.getMessage(), "HCL Assist");
                    });
                }
            }
        }.queue();
    }
}
