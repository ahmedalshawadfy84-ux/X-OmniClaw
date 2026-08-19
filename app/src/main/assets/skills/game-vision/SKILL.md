---
name: game-vision
description: |
  للتعامل مع ألعاب Android التي تعتمد على Unity / Unreal / Cocos / SurfaceView حيث تكون شجرة Accessibility فارغة. استخدم الرؤية البصرية VLM بدلا من refs.
  Activate when user asks to play, control, or automate any Android game, especially Unity games, or when snapshot shows [🎮 UNITY/GAME MODE DETECTED] or refs=0.
metadata:
  {
    "xomniclaw": {
      "always": false,
      "emoji": "🎮",
      "version": "1.0.0",
      "category": "gaming"
    }
  }
---

# Game Vision Skill - التحكم في ألعاب Unity عبر الرؤية البصرية

هذه المهارة لحل مشكلة أن X-OmniClaw لا يرى ألعاب Unity لأنها ترسم على `SurfaceView` عبر GPU وليس عبر Android Views التقليدية.

## 🎯 When to Use

- المستخدم يقول: "العب اللعبة"، "اضغط زر البدء في اللعبة"، "أوتوماتيك للعبة"
- snapshot يرجع `refs=0` أو `game_surface` فقط
- snapshot يحتوي على `[🎮 UNITY/GAME MODE DETECTED]`
- اسم الحزمة يوحي بلعبة (com.tencent.tmgp, com.miHoYo, com.king, etc)

## 🧠 المبدأ الأساسي

**لا تستخدم `ref` نهائياً في الألعاب.** استخدم فقط:

1. `device(action="act", kind="tap", target="وصف بصري دقيق")` → سيعمل Dual-Track VLM grounding تلقائياً
2. `device(action="screenshot")` → للحصول على صورة والتحقق
3. `device(action="act", kind="swipe", startX, startY, endX, endY)` → joystick / سحب
4. `device(action="act", kind="long_press", x, y)` → الضغط المطول

## 📋 Workflow - التحكم في لعبة

### الخطوة 1: اكتشاف وضع اللعبة

```kotlin
device(action="snapshot")
```

إذا رأيت:
- `refs=0` أو `refs=1` مع `game_surface`
- بانر `[🎮 UNITY/GAME MODE DETECTED]`
→ أنت في وضع اللعبة، تابع الخطوات التالية

### الخطوة 2: الحصول على لقطة شاشة (اختياري لكن مفيد)

```kotlin
device(action="screenshot")
```

تحقق من الصورة (المسار في النتيجة) لمعرفة مكان الأزرار

### الخطوة 3: الضغط باستخدام الوصف البصري (VLM Grounding)

هذا هو **القلب** للمهارة:

```kotlin
// أمثلة صحيحة - وصف بصري دقيق:
device(action="act", kind="tap", target="green PLAY button at center bottom with white text")
device(action="act", kind="tap", target="red attack button on bottom right corner")
device(action="act", kind="tap", target="X close button top right")
device(action="act", kind="tap", target="settings gear icon top left corner")
device(action="act", kind="tap", target="joystick circle at bottom left")
device(action="act", kind="tap", target="character icon in the middle of screen")
```

**قواعد كتابة target:**

- اذكر **اللون** (green, red, blue, white, golden)
- اذكر **الموقع** (top left, bottom center, center, bottom right)
- اذكر **الشكل / النص** (PLAY button, attack icon, gear icon, X button)
- اذكر **السياق** (on the main menu, in battle, on pause screen)
- **لا تكتب عربي في target**، استخدم انجليزي فقط لأن VLM مدرب على انجليزي

❌ خطأ:
```kotlin
device(action="act", kind="tap", ref="e1") // refs غير موجودة في اللعبة
device(action="act", kind="tap", target="زر البدء") // عربي لا يفهمه VLM جيداً
device(action="act", kind="tap", target="button") // غامض جداً
```

✅ صح:
```kotlin
device(action="act", kind="tap", target="green PLAY button with white text at bottom center")
```

### الخطوة 4: مركّبات Joystick والحركة

للألعاب التي فيها joystick:

```kotlin
// حركة Joystick: سحب من مركز الـ joystick باتجاه معين
// Joystick عادة في أسفل اليسار
device(action="act", kind="swipe", startX=200, startY=1600, endX=200, endY=1300, durationMs=500) // للأمام
device(action="act", kind="swipe", startX=200, startY=1600, endX=400, endY=1600, durationMs=500) // يمين
device(action="act", kind="swipe", startX=200, startY=1600, endX=50, endY=1600, durationMs=500) // يسار

// سحب الشاشة للنظر حولك (في ألعاب 3D)
device(action="act", kind="swipe", startX=540, startY=800, endX=200, endY=800, durationMs=400) // دوران الكاميرا
```

### الخطوة 5: التحقق بعد كل فعل

```kotlin
device(action="act", kind="wait", timeMs=1200)
device(action="screenshot") // أو snapshot
```

**مهم:** الألعاب فيها animation، انتظر 1-2 ثانية بين الأفعال.

## 🎮 سيناريوهات شائعة

### سيناريو 1: بدء لعبة

```
المستخدم: "ابدأ اللعبة X"
1. device(action="snapshot") → تتأكد أنها لعبة
2. device(action="act", kind="tap", target="PLAY button or START button at center bottom green color")
3. wait 1.5s
4. snapshot/screenshot → هل دخلنا اللعبة؟
```

### سيناريو 2: قتال / هجوم

```
المستخدم: "اهجم على العدو"
1. device(action="act", kind="tap", target="attack button bottom right red circle")
2. أو: device(action="act", kind="tap", target="sword attack icon bottom right")
3. wait
4. screenshot للتحقق من الضرر
```

### سيناريو 3: جمع موارد

```
المستخدم: "اجمع الذهب"
1. device(action="act", kind="tap", target="gold coin or loot chest shining on ground center of screen")
```

### سيناريو 4: فتح إعدادات اللعبة والخروج

```
1. device(action="act", kind="tap", target="pause button top right or gear settings icon")
2. wait
3. device(action="act", kind="tap", target="exit or quit or X button")
```

## 🛠️ Tool Usage

**الأدوات المسموحة:**

```kotlin
device(action="snapshot") // لرؤية metadata + banner لعبة
device(action="screenshot") // لرؤية بصرية
device(action="act", kind="tap", target="...") // الأساس
device(action="act", kind="tap", x=Int, y=Int, target="...") // مع إحداثيات تقريبية
device(action="act", kind="swipe", startX, startY, endX, endY, durationMs=Int)
device(action="act", kind="long_press", x, y)
device(action="act", kind="wait", timeMs=Int)
```

## ⚠️ Common Pitfalls & Anti-patterns

| ❌ خطأ | ✅ صح | السبب |
|---|---|---|
| `ref="e5"` في لعبة | `target="play button"` | refs غير موجودة في Unity |
| `target="زر"` عربي | `target="green button"` انجليزي | VLM أفضل في الانجليزية |
| tap واحد واعتباره انتهى | tap + wait + screenshot | الألعاب تحتاج تحقق |
| `device(open)` لفتح عنصر داخل اللعبة | tap بـ target | الألعاب كلها داخل Surface واحد |
| نسيان الانتظار | wait 1-2 ثانية | الأنيميشن يحتاج وقت |

## 🔄 Error Handling

### إذا فشل الضغط البصري:

```kotlin
// جرّب وصفاً مختلفاً
device(action="act", kind="tap", target="big green button at center") // بدل "play"

// جرّب مع إحداثيات Grid المرجعية إذا ذكرها الـ banner
// مثال الشاشة 1080x2400:
// PLAY عادة bottom-center: (540, 1800)
// CLOSE عادة top-right: (945, 300)
device(action="act", kind="tap", x=540, y=1800, target="play button")
```

### إذا كانت الشاشة سوداء / تحميل:

```kotlin
device(action="act", kind="wait", timeMs=3000)
device(action="screenshot")
```

## 💡 Complete Example - تشغيل لعبة Subway Surfers

```
المستخدم: "شغّل Subway Surfers واضغط play"

1. device(action="open", package_name="com.kiloo.subwaysurf")
2. wait 2000
3. device(action="snapshot")
   → ترجع [🎮 UNITY/GAME MODE] refs=1 game_surface
4. device(action="screenshot")
5. device(action="act", kind="tap", target="green PLAY button at bottom center with white text")
6. wait 1500
7. device(action="screenshot") → هل بدأت الجري؟
8. إذا بدأت: device(action="act", kind="swipe", startX=540, startY=1600, endX=540, endY=800, durationMs=300) // قفز
```

## 📝 للـ LLM: تذكير نهائي

- في وضع اللعبة، **لا تعتمد على snapshot refs**
- **كل تفاعل يجب أن يكون target-based**
- استخدم **لغة إنجليزية وصفية دقيقة** للـ target
- **انتظر وتحقق** بعد كل إجراء
- إذا رأيت بانر `[🎮 UNITY/GAME MODE]`، اطبع في تفكيرك: "Game mode - switching to visual grounding"
