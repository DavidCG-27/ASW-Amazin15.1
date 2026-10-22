<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <title>Error de Autorización - Amazin</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .error-box { border: 1px solid red; padding: 20px; background-color: #ffe6e6; }
        h2 { color: #cc0000; }
    </style>
</head>
<body>
    <div class="error-box">
        <h2>Acceso Denegado</h2>
        <p>
            <!-- Muestra el mensaje enviado desde el AuthorizationInterceptor -->
            <strong>Motivo:</strong> ${not empty errorMessage ? errorMessage : "Operación no permitida o error inesperado."}
        </p>
    </div>
    <br>
    <a href="index.html">Volver al inicio</a>
</body>
</html>
