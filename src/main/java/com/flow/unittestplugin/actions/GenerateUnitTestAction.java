package com.flow.unittestplugin.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import net.minidev.json.JSONObject;


public class GenerateUnitTestAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null) return;

        String sourceCode = Objects.requireNonNull(psiFile.getText());
        var fileName = psiFile.getName().replaceFirst("(\\.[^.]+)?$", "Test$1");

        createTestClass(project, psiFile, fileName, sourceCode, psiFile.getLanguage());
    }

    private void createTestClass(Project project, PsiFile sourceFile, String testClassName, String sourceCode, Language language) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                PsiFile containingFile = PsiUtilBase.getPsiFile(project, sourceFile.getVirtualFile());
                PsiFile newFile = Objects.requireNonNull(containingFile.getContainingDirectory().createFile(testClassName));
                Document document = Objects.requireNonNull(newFile.getViewProvider().getDocument());

                String response = getTest(language.getDisplayName(), sourceCode);

                StringBuilder indentedResponse = new StringBuilder();
                String[] lines = response.split("\n");
                for (String line : lines) {
                    indentedResponse.append("\t").append(line).append("\n");
                }

                document.setText(indentedResponse.toString());

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private String getTest(String language, String sourceCode) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("prompt", "Você é um experiente desenvolvedor sênior. \\n Seu trabalho hoje será criar o teste unitário do código " + language + " a seguir: " + sourceCode + "Simule um teste de mutação e garanta que o teste tenha cobertura adequada para as possíveis mutações do código, não inclua comentários. " +
                    " Inclua todos as importações necessarias. Caso contrario será considerado incorreto, \\n Ignore os métodos privados.\\n . Atenção, não inclua nenhuma explicação e nenhum comentário, apenas o código. Caso houver qualquer comentário gere em inglês se o texto inicial for em inglês.");
            requestBody.put("temperature", 0.7);

            HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:3000/v1/getResponseOpenAI").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine).append("\n");
                }
            }
            return response.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

}
