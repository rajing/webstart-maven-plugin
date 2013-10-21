package org.codehaus.mojo.webstart.sign;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class UpdateManifest {
  public static File process(File inputFile, Map manifest) throws Exception {
    File outputFile = new File(inputFile.getParent(), "MANIFEST-" + inputFile.getName());
    if (!outputFile.exists()) {
      outputFile.createNewFile();
    }
    File inputName = new File(inputFile.getParent(), inputFile.getName());
    JarFile inputJarFile = new JarFile(inputFile);
    Enumeration enumeration = inputJarFile.entries();
    FileOutputStream fos = new FileOutputStream(outputFile);
    JarOutputStream jos = new JarOutputStream(fos);
    boolean manifestProcessed = false;
    while (enumeration.hasMoreElements()) {
      JarEntry je = (JarEntry) enumeration.nextElement();
      manifestProcessed = process(inputJarFile, jos, je, manifest) || manifestProcessed;
    }
    if (!manifestProcessed) { // No Manifest found, so create one
      System.err.println(inputFile.getAbsolutePath() + " had no Manifest. Creating one.");
      LinkedHashMap manifestEntries = new LinkedHashMap(manifest);
      writeManifest(jos, manifestEntries);
    }
    jos.close();
    inputJarFile.close();
    outputFile.setLastModified(inputFile.lastModified());
    inputFile.delete();
    outputFile.renameTo(inputName);
    return inputName;
  }

  private static void writeManifest(JarOutputStream jos, LinkedHashMap manifestEntries)
      throws IOException {
    JarEntry je = new JarEntry("META-INF/MANIFEST.MF");
    
    StringBuffer sb = new StringBuffer();
    for (Iterator i = manifestEntries.entrySet().iterator(); i.hasNext();) {
      Map.Entry me = (Map.Entry) i.next();
      sb.append(me.getKey()).append(": ").append(me.getValue()).append("\n");
    }
    ByteArrayInputStream is = new ByteArrayInputStream(sb.toString().getBytes());
    addEntry(je, jos, is);
  }

  private static void parseManifest(BufferedReader br, LinkedHashMap entries) throws IOException {
    StringBuffer entry = null;
    String line = null;
    while(null != (line = br.readLine())) {
      // new key
      if (!line.startsWith(" ")) {
        parseEntry(entry, entries);
        entry = new StringBuffer();
      }
      entry.append(line).append("\n");
    }
    
    // the last entry should be parsed as well
    parseEntry(entry, entries);
  }

  private static void parseEntry(StringBuffer entry, LinkedHashMap entries) {
    // very first entry => nothing read 
    if (null == entry || 0 == entry.toString().trim().length()) {
      return;
    }
    final int separator = entry.indexOf(":");
    entries.put(entry.substring(0, separator), entry.substring(separator + 1).trim());
  }

  private static boolean process(JarFile jarFile, JarOutputStream jos, JarEntry entry, Map manifest)
      throws Exception {
    boolean wasManifest = false;
    InputStream is = jarFile.getInputStream(entry);
    if (entry.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
      wasManifest = true;
      BufferedReader orig = new BufferedReader(new InputStreamReader(is));
      LinkedHashMap manifestEntries = new LinkedHashMap();
      // manifestEntries.putAll(jarFile.getManifest().getEntries());
      parseManifest(orig, manifestEntries);
      manifestEntries.putAll(manifest);
      writeManifest(jos, manifestEntries);
    } else {
      addEntry(entry, jos, is);
    }
    return wasManifest;
  }

  private static void addEntry(JarEntry je, JarOutputStream jos, InputStream is) throws IOException {
    jos.putNextEntry(new JarEntry(je.getName()));
    byte[] buffer = new byte[4096];
    int bytesRead = 0;
    while ((bytesRead = is.read(buffer)) != -1) {
        jos.write(buffer, 0, bytesRead);
    }
    is.close();
    jos.flush();
    jos.closeEntry();
  }
}

