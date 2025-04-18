# 🛠️ JarRenamer

**JarRenamer** is a simple desktop application that helps you quickly rename **classes, methods, and fields** inside a `.jar` file.

It's a handy tool for developers who need to:
- Clean up or refactor compiled Java code
- De-obfuscate obfuscated `.jar` files
- Modify `.jar` structure without needing full source code

---

## 📸 Screenshot

Here's how it looks in action:

<img src="https://your-image-host.com/jarrenamer-screenshot.png" alt="JarRenamer UI" width="600"/>

---

## ✨ Features

- 🔍 Load any `.jar` file
- 🧬 Display all classes, methods, and fields
- ✏️ Rename them with one click
- 💾 Save/export modified `.jar`

---

## 🎮 Simple Usage

1. Open the app
2. Click **Browse**
3. Browse the class tree
4. Double-click to rename any name
5. Click **Export** to save changes

---

## 💻 Technologies

- Java 17+
- JavaFX (FXML UI)
- ASM (or Javassist)
- Gradle

---

## 🔧 Project Structure
```
src/main/ 
├── java/main.jarrenamer/ │ 
  ├── JarRenamerApp.java # App entry point │ 
  ├── JarRenamerController.java # UI event handling │ 
  └── JarRenamerService.java # Core renaming logic │
├── resources/ │ 
  ├── main.jarrenamer/hello-view.fxml # UI layout │ 
  └── styles/main.css # UI styles
```
  
---

## 📥 Getting Started

```bash
git clone https://github.com/duykhongphai/JarRenamer.git
cd JarRenamer
./gradlew run
```
  
---

##📄 License
This project is licensed under the MIT License.
