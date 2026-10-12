package dev.hclassist;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.*;

public final class IntegrationTest extends BasePlatformTestCase {
    @Override protected void setUp() throws Exception {
        super.setUp();
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    }
    public void testPackerCompletion() {
        myFixture.configureByText("image.pkr.hcl", "build { provisioner \"shell\" {\n  <caret>\n} }");
        var items = myFixture.completeBasic();
        assertNotNull(items);
        var names = Arrays.stream(items).map(i -> i.getLookupString()).toList();
        assertTrue(names.toString(), names.contains("inline"));
        assertTrue(names.toString(), names.contains("scripts"));
        assertFalse(names.toString(), names.contains("resource"));
    }
    public void testVirtualBoxRequiredFieldsAfterBrace() {
        myFixture.configureByText("main.pkr.hcl", "source \"virtualbox-iso\" \"name\" <caret>");
        myFixture.type('{');
        waitForPackerField("iso_checksum");
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue(text, text.matches("(?s).*iso_url\\s*=\\s*\"\".*"));
        assertTrue(text, text.matches("(?s).*ssh_username\\s*=\\s*\"\".*"));
        assertFalse(text, text.contains("guest_os_type ="));
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    }
    public void testVirtualBoxRequiredFieldsAfterEnter() {
        myFixture.configureByText("main.pkr.hcl", "source \"virtualbox-iso\" \"name\" {<caret>}");
        myFixture.type('\n');
        waitForPackerField("iso_checksum");
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    }
    public void testVirtualBoxIntentionPreservesAlternatives() {
        myFixture.configureByText("main.pkr.hcl", "source \"virtualbox-iso\" \"name\" {\n iso_urls = [\"keep.iso\"]\n communicator = \"none\"\n <caret>\n}");
        myFixture.launchAction(myFixture.findSingleIntention("Insert missing required HCL fields"));
        waitForPackerField("iso_checksum");
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue(text, text.contains("keep.iso"));
        assertFalse(text, text.matches("(?s).*\\biso_url\\s*=.*"));
        assertFalse(text, text.matches("(?s).*ssh_username\\s*=.*"));
    }
    public void testVirtualBoxWinrmCommunicator() {
        myFixture.configureByText("main.pkr.hcl", "source \"virtualbox-iso\" \"name\" {\n communicator = \"winrm\"\n <caret>\n}");
        myFixture.launchAction(myFixture.findSingleIntention("Insert missing required HCL fields"));
        waitForPackerField("winrm_username");
        assertFalse(myFixture.getEditor().getDocument().getText().contains("ssh_username ="));
    }
    private void waitForPackerField(String field) {
        PlatformTestUtil.waitWithEventsDispatching("Missing Packer field: " + field, () ->
                myFixture.getEditor().getDocument().getText().matches("(?s).*" + field + "\\s*=\\s*\"\".*"), 20);
    }
    public void testAmazonRequiredFields() {
        startPacker("source \"amazon-ebs\" \"image\" <caret>", "ami_name");
        assertTrue(myFixture.getEditor().getDocument().getText().contains("instance_type"));
        assertFalse(myFixture.getEditor().getDocument().getText().contains("access_key"));
    }
    public void testQemuRequiredFields() {
        startPacker("source \"qemu\" \"image\" <caret>", "iso_checksum");
        assertTrue(myFixture.getEditor().getDocument().getText().contains("ssh_username"));
    }
    public void testDockerRequiredFields() {
        startPacker("source \"docker\" \"image\" <caret>", "image");
        assertTrue(myFixture.getEditor().getDocument().getText().matches("(?s).*commit\\s*=\\s*true.*"));
        assertFalse(myFixture.getEditor().getDocument().getText().contains("ssh_username"));
    }
    public void testShellRequiredFields() {
        startPacker("build { provisioner \"shell\" <caret> }", "script");
    }
    public void testFileRequiredFields() {
        startPacker("build { provisioner \"file\" <caret> }", "destination");
        assertTrue(myFixture.getEditor().getDocument().getText().contains("source"));
    }
    public void testAnsibleRequiredFields() {
        startPacker("build { provisioner \"ansible\" <caret> }", "playbook_file");
    }
    public void testPostProcessorRequiredFields() {
        startPacker("build { post-processors { post-processor \"shell-local\" <caret> } }", "script");
    }
    public void testDockerBuildRequiredFields() {
        startPacker("source \"docker\" \"image\" { build <caret> }", "path");
    }
    public void testPackerProjectRequirements() {
        myFixture.addFileToProject(".hcl-assist.packer.json", """
            {"version":1,"blocks":{"source:custom":{"fields":[{"name":"endpoint"}]}}}
            """);
        var file = myFixture.addFileToProject("main.pkr.hcl", "source \"custom\" \"image\" ");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());
        myFixture.type('{');
        waitForPackerField("endpoint");
    }
    public void testAmazonIntentionPreservesFilter() {
        myFixture.configureByText("main.pkr.hcl", "source \"amazon-ebs\" \"image\" {\n source_ami_filter { owners = [\"amazon\"] }\n <caret>\n}");
        myFixture.launchAction(myFixture.findSingleIntention("Insert missing required HCL fields"));
        waitForPackerField("ami_name");
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue(text, text.contains("owners"));
        assertFalse(text, text.matches("(?s).*\\bsource_ami\\s*=.*"));
    }
    private void startPacker(String text, String field) {
        myFixture.configureByText("main.pkr.hcl", text);
        myFixture.type('{');
        waitForPackerField(field);
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    }
    public void testNomadDriverCompletion() {
        myFixture.configureByText("web.nomad", "job \"web\" { group \"g\" { task \"t\" { driver = \"exec\"\nconfig {\n<caret>\n} } } }");
        var items = myFixture.completeBasic();
        assertNotNull(items);
        var names = Arrays.stream(items).map(i -> i.getLookupString()).toList();
        assertTrue(names.toString(), names.contains("command"));
        assertFalse(names.toString(), names.contains("image"));
    }
    public void testRequiredFieldsAfterBrace() {
        myFixture.configureByText("main.tf", "resource \"random_id\" \"example\" <caret>");
        myFixture.type('{');
        PlatformTestUtil.waitWithEventsDispatching("Required resource fields were not inserted", () ->
                myFixture.getEditor().getDocument().getText().contains("byte_length = 0"), 20);
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
        assertFalse(myFixture.getEditor().getDocument().getText().contains("keepers ="));
        myFixture.type("16");
        assertTrue(myFixture.getEditor().getDocument().getText().contains("byte_length = 16"));
    }
    public void testRequiredFieldsAfterEnter() {
        myFixture.configureByText("main.tf", "resource \"random_id\" \"example\" {<caret>}");
        myFixture.type('\n');
        PlatformTestUtil.waitWithEventsDispatching("Enter did not start the required-fields template", () ->
                myFixture.getEditor().getDocument().getText().contains("byte_length = 0"), 20);
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    }
    public void testIntentionPreservesExistingFields() {
        myFixture.configureByText("main.tf", "resource \"random_id\" \"example\" {\n  prefix = \"keep-me\"\n  <caret>\n}");
        myFixture.launchAction(myFixture.findSingleIntention("Insert missing required HCL fields"));
        PlatformTestUtil.waitWithEventsDispatching("Missing field intention did not insert the field", () ->
                myFixture.getEditor().getDocument().getText().contains("byte_length = 0"), 20);
        assertTrue(myFixture.getEditor().getDocument().getText().contains("prefix = \"keep-me\""));
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    }
    public void testProjectSchemaTakesPriority() {
        myFixture.addFileToProject(".hcl-assist.providers.json", """
            {"format_version":"1.0","provider_schemas":{"registry.terraform.io/hashicorp/random":{
              "resource_schemas":{"random_id":{"block":{"attributes":{
                "custom_required":{"type":"string","required":true}
              }}}}
            }}}
            """);
        var file = myFixture.addFileToProject("main.tf", "resource \"random_id\" \"example\" ");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());
        myFixture.type('{');
        PlatformTestUtil.waitWithEventsDispatching("Local provider schema was not used", () ->
                myFixture.getEditor().getDocument().getText().contains("custom_required = \"\""), 20);
        assertFalse(myFixture.getEditor().getDocument().getText().contains("byte_length"));
    }
}
