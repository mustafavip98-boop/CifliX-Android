# CifliX Player — Native Android

مشروع Android أصلي مستقل عن ملفات المشروع السابق. هذه النسخة حالياً هي هيكل واجهة أولي وليست مشغلاً مكتملاً.

## التقنية
- Kotlin + Jetpack Compose + Material 3
- AndroidX Media3 / ExoPlayer مع دعم HLS
- OkHttp وKotlin Serialization
- Room للتخزين المحلي (الربط قيد التطوير)

## مراحل التطوير
1. استيراد M3U/M3U8 وتشغيل HLS.
2. تخزين المصادر والمفضلة وسجل المشاهدة.
3. موائمات JSON Manifest وStremio.
4. موائم CloudStream للمستودعات المتوافقة.
5. Xtream Codes وXMLTV EPG.
6. اختبارات وتحسين تجربة الاستخدام.

يجب استخدام المصادر والمحتوى المصرح لك بالوصول إليها فقط.
