module main.jarrenamerapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.materialdesign2;
    requires org.objectweb.asm;
    requires org.objectweb.asm.commons;

    opens main.jarrenamerapp to javafx.fxml;
    exports main.jarrenamerapp;
}