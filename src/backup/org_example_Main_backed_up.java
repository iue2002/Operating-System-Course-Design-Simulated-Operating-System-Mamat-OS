/*
 Backup of org.example.Main created on user request.
 Original file path: src/main/java/org/example/Main.java

 The original contents were preserved here to allow recovery if needed.
 Below are the original versions (two variants) kept as reference —
 they are intentionally commented out so this file is not compiled.

 ----- Variant 1 -----
 package org.example;

 // The original org.example.Main was backed up to src/backup/org_example_Main_backed_up.java
 // This placeholder file is left to avoid accidental compilation of a duplicate entrypoint.

 public class Main {
     public static void main(String[] args) {
         System.out.println("This project uses os-core/com.os.Main as the runtime entrypoint.");
         System.out.println("To run the simulator, run the Main in the os-core module (com.os.Main).");
         System.out.println("Example (from project root): java -cp os-core\\classes com.os.Main");
     }
 }

 ----- Variant 2 -----
 package org.example;

 public class Main {
     public static void main(String[] args) throws Exception {
         // Delegate to the canonical kernel demo entrypoint in os-core
         com.os.Main.main(args);
     }
 }

 End of backup content.
*/

