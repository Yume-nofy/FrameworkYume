#!/bin/bash

# Paramètres
SRC_DIR="src/nofy/p17"
LIB_DIR="lib"
CLASSES_DIR="../test_sprint/web/WEB-INF/classes"  # ← Directement dans web/

echo "=== COMPILATION ==="

# Créer le dossier de sortie s'il n'existe pas
mkdir -p "$CLASSES_DIR"

# Compilation
echo "Compilation des sources..."
if ! javac -cp "$LIB_DIR/*" -d "$CLASSES_DIR" $SRC_DIR/*.java; then
    echo "❌ Échec de la compilation"
    exit 1
fi

# Vérification
if [ -f "$CLASSES_DIR/nofy/p17/FrontServlet.class" ]; then
    echo "✅ Compilation réussie - FrontServlet.class trouvé"
else
    echo "❌ FrontServlet.class introuvable après compilation"
    exit 1
fi

echo "✅ Compilation terminée avec succès"