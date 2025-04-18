# 🛠️ JarRenamer

**JarRenamer** is a simple desktop application that helps you quickly rename **classes, methods, and fields** inside a `.jar` file.

It's a handy tool for developers who need to:
- Clean up or refactor compiled Java code
- De-obfuscate obfuscated `.jar` files
- Modify `.jar` structure without needing full source code

---

## ✨ Features

- Open any `.jar` file
- View all classes, methods, and fields
- Rename selected elements with ease
- Export the modified `.jar` file

---

## 📦 How to Use

1. Launch the app
2. Open a `.jar` file
3. Select the class, method, or field you want to rename
4. Enter a new name and confirm
5. Save/export the updated `.jar`

---

## 💻 Technologies

- Java
- JavaFX for the user interface
- ASM or similar library for bytecode editing
- Gradle for project build

---

## 🔧 Project Structure

src/main/ 
├── java/main.jarrenamer/ │ 
  ├── JarRenamerApp.java # App entry point │ 
  ├── JarRenamerController.java # UI event handling │ 
  └── JarRenamerService.java # Core renaming logic │
├── resources/ │ 
  ├── main.jarrenamer/hello-view.fxml # UI layout │ 
  └── styles/main.css # UI styles

  
---

## 📥 Getting Started

```bash
git clone https://github.com/duykhongphai/JarRenamer.git
cd JarRenamer
./gradlew run

📄 License
This project is licensed under the MIT License.
