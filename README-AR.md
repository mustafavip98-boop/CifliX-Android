# CifliX Android APK wrapper

هذا مشروع Android يحوّل واجهة CifliX الحالية إلى تطبيق WebView مع الاحتفاظ بملفات HTML/CSS/JS.

## بناء APK

يمكن فتح المجلد في Android Studio ثم اختيار Build > Build APK(s)، أو رفع المشروع إلى GitHub وتشغيل GitHub Actions باسم **Build CifliX APK**. سيظهر APK كـ Artifact باسم `CifliX-APK`.

## ربط API

بعد تثبيت التطبيق اضغط زر ⚙ أعلى اليمين، وأدخل عنوان API عام وآمن بصيغة HTTPS وينتهي بـ `/api`، مثال: `https://example-host.com/api`.

بدون API عام سيعرض التطبيق بيانات تجريبية فقط. عنوان `127.0.0.1` أو `localhost` لا يصلح لأجهزة المستخدمين الآخرين. يجب نشر Backend FastAPI على استضافة عامة أولاً، والتأكد من السماح بـ CORS من `https://appassets.androidplatform.net` أو `*`.

استخدم فقط بيانات وروابط بث لديك تصريح قانوني لاستضافتها وتوزيعها. لا يتضمن هذا المشروع تجاوز DRM أو قيود الوصول.
