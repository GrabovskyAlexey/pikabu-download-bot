🚨 КРИТИЧЕСКАЯ ОШИБКА

💬 Сообщение: ${error.errorMessage}
🕐 Время: ${error.occurredAt?string("yyyy-MM-dd HH:mm:ss")}

<#if error.pageUrl??>
📄 URL: ${error.pageUrl}

</#if>
<#if error.stackTrace?? && error.stackTrace?length < 500>
Stack trace:
```
${error.stackTrace}
```

</#if>
Требуется немедленное внимание!
