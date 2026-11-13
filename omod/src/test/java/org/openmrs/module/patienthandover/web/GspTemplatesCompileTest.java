package org.openmrs.module.patienthandover.web;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import groovy.text.SimpleTemplateEngine;
import org.junit.Test;

/**
 * Compiles every .gsp page the way the OpenMRS UI framework does, so a template error (for example
 * a backslash in inline JavaScript, which Groovy treats as its own escape) fails the build instead
 * of the page at run time.
 */
public class GspTemplatesCompileTest {
	
	@Test
	public void allPagesShouldCompile() throws Exception {
		File dir = new File("src/main/webapp/pages");
		File[] pages = dir.listFiles();
		assertTrue("pages folder not found: " + dir.getAbsolutePath(), pages != null && pages.length > 0);
		List<String> errors = new ArrayList<String>();
		SimpleTemplateEngine engine = new SimpleTemplateEngine();
		for (File page : pages) {
			if (!page.getName().endsWith(".gsp"))
				continue;
			try {
				engine.createTemplate(new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8));
			}
			catch (Exception e) {
				errors.add(page.getName() + ": " + e.getMessage());
			}
		}
		if (!errors.isEmpty())
			fail("Templates that do not compile:\n" + String.join("\n", errors));
	}
}
