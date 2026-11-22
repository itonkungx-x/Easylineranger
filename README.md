# Meowto Ranger — v0.13.0 preview: Teach Mode (สอนบอท)

ภารกิจประจำวันกับฟาร์มด่านทำงานแบบ "สอนครั้งเดียว แล้วให้บอทเล่นซ้ำ" แต่ละเครื่องสอนเองจากจอของตัวเอง จึงใช้ได้กับทุกขนาดจอ และไม่ต้องรู้ตำแหน่งปุ่มของเกมล่วงหน้า

## ปุ่มบนแถบลอยในเกม
| ปุ่ม | ทำอะไร |
|---|---|
| ♥ | ส่งคะแนนมิตรภาพ (ระบบเดิม) |
| 📋 | เล่นภารกิจประจำวันตามที่สอนไว้ หนึ่งรอบ |
| ⚔ | ฟาร์มด่านตามที่สอนไว้ วนซ้ำจนหาขั้นแรกไม่เจอ (เช่น พลังงานหมด) สูงสุด 100 รอบ |
| ▶▶ | ทำทั้งหมด: ♥ → 📋 → ⚔ ข้ามอันที่ยังไม่ได้สอนหรือยังไม่ได้อนุญาต |
| 🎓 | สอน: เลือก สอน 📋 หรือ สอน ⚔ เล่นตามปกติ แล้วกด ✓ บันทึก |
| ■ | หยุดทุกอย่าง |

## วิธีสอน
1. เริ่มจาก**หน้าหลัก** เล่นตามปกติหนึ่งรอบ แล้วกลับมาจบที่**หน้าหลัก**
2. แตะช้า ๆ ทีละปุ่ม (แอปต้องจับภาพก่อนส่งการแตะต่อให้เกม)
3. ตอนสู้ในด่าน ให้แตะรัว ๆ ที่ช่องเรนเจอร์ แอปจะจำเป็น "ขั้นแตะรัว" ที่แตะต่อไปเรื่อย ๆ จนเห็นปุ่มขั้นถัดไป (เช่น หน้าชนะ)
4. **ห้ามแตะปุ่มที่ใช้ Ruby ตอนสอน**

## กฎความปลอดภัยตอนเล่นซ้ำ
- แตะเฉพาะจุดที่หน้าตาเหมือนตอนสอน และต้องเห็นตรงกันสองภาพติดกัน ไม่แตะพิกัดมั่ว
- ถ้าขั้นไหนไม่ขึ้นภายในเวลาที่กำหนด จะข้ามขั้นนั้น ถ้าข้ามติดกัน 3 ขั้นจะหยุดทันที ป๊อปอัปแปลก ๆ เช่น "ซื้อพลังงานด้วย Ruby" จึงไม่ถูกแตะ
- ตอนเริ่มรัน ถ้ายังหาขั้นแรกไม่เจอ จะกด Back สูงสุด 4 ครั้ง เพื่อปิดป๊อปอัปหรือกลับไปหน้าที่สอนไว้
- แตะหรือปัดได้เฉพาะตอนที่ LINE Rangers อยู่หน้าจอเท่านั้น
- ถ้าเปลี่ยนเครื่อง หมุนจอคนละแบบ หรือเกมอัปเดตหน้าตา ให้สอนใหม่

## ข้อจำกัด
- ยังไม่เคยทดสอบบนมือถือจริงหรือกับเกมจริง
- ป๊อปอัปที่ไม่ได้อยู่ในตอนสอน (อีเวนต์ใหม่) อาจทำให้บอทหยุดกลางทาง ถ้าเป็นแบบนี้ให้สอนใหม่
- ระบบ ♥ ส่งคะแนนยังใช้ตำแหน่งตายตัวแบบเดิม อาจใช้ไม่ได้กับจอที่สัดส่วนต่างจากเครื่องที่ใช้ตั้งค่ามาก

## โค้ด
- `modules/routine/RoutineModel.kt`: จำหน้าตาปุ่ม, หาปุ่มบนจอ, จัดกลุ่มการแตะเป็นขั้น (มี unit test)
- `modules/routine/RoutineRecorder.kt`: โหมดสอน
- `modules/routine/RoutinePlayer.kt`: เล่นซ้ำพร้อมกฎความปลอดภัย
- `modules/routine/RoutineStore.kt`: บันทึกเป็น JSON ในที่เก็บส่วนตัวของแอป
- ลบโค้ด Wireless Debugging / MediaProjection ของ v0.9 ที่ไม่ได้ใช้แล้ว

---

# Meowto Ranger — v0.12.0 preview: Update Checker Fix

## Why v0.10 showed HTTP 404
The old app called GitHub API `/releases/latest`. GitHub returns 404 because there are **no published official Releases** yet; only successful CI preview builds were available from GitHub Actions.

## Fixed in v0.12
- If no official GitHub Release exists, the app now checks the latest successful `main` GitHub Actions CI run.
- The candidate version is read from `app/build.gradle.kts` at **that exact successful commit**, not from an unbuilt HEAD or a hardcoded number.
- For a newer preview, the app displays its version and opens the exact GitHub Actions run page with the APK artifact.
- The app displays the installed Android version dynamically instead of hardcoding the heading.
- If no newer build exists, the app reports it instead of showing HTTP 404.
- Official signed GitHub Release updates still download with SHA-256 validation and Android installer confirmation, as before.

## Important limitation
This is **not a one-tap update channel yet**. GitHub Actions preview APKs may be signed with different temporary debug keys, so installing one over another can fail. It may require uninstalling the old preview, losing local app data and re-enabling Accessibility. For true in-place updates, configure the long-lived private release keystore in GitHub Actions secrets and publish an official signed Release. See `docs/RELEASING.md`. Never commit signing keys or passwords.

---

# Meowto Ranger — v0.11.0 Accessibility preview

This release keeps the v0.10 Accessibility-based LINE Rangers friend helper and automatic self-disable outside the game.

## Faster scrolling and mid-list recovery
- Swipe duration: **480 ms** (previously 720 ms).
- Rest after swipe: **650 ms** (previously 850 ms).
- Settle probe: **320 ms** (previously 410 ms).
- Verify a green help button in **two separate screenshots** before tapping.
- If the carousel does not advance, retry with a longer swipe rather than stopping after four unchanged images; stop only after nine unchanged attempts or the bounded scan limit.
- Allow 2.2 seconds before automatic Accessibility shutoff outside LINE Rangers, to reduce false shutdown from transient system overlays. K PLUS-specific shutdown remains immediate.

**Android limitation:** The app can disable its own Accessibility service using `disableSelf()`, but cannot re-enable it silently. You must turn the service on again in Android Accessibility Settings. This preview has **not** been verified on the physical phone or with K PLUS.

---

## Previous release: v0.10.0 Accessibility preview

LINE Rangers friendship-point helper. The user starts the friend-point workflow inside the game via the visible floating play control and can stop it at any time.

## Accessibility + banking compatibility

- This preview **restores Android AccessibilityService** for game-only screenshots and gestures. Wireless Debugging and MediaProjection are **not required** for this mode.
- When the device leaves LINE Rangers after the service has seen the game, the service **calls Android AccessibilityService.disableSelf()**. This genuinely turns off the accessibility permission. It does not merely pause the bot or pretend that the service is disabled.
- The **ปิด Accessibility ทันที (ก่อนใช้ K PLUS)** button can also disable the service before opening the bank app.
- If K PLUS is detected while this service is still active, the service tries to turn itself off immediately. **The bank may check earlier**, so compatibility is not guaranteed. Follow any security warnings from the bank.
- Android **does not let ordinary apps enable AccessibilityService programmatically**. Each time the service has disabled itself, the user must re-enable it in **Android Settings → Accessibility**, then return to the app or game. The app includes a shortcut to that screen.
- No Root, Shizuku, hidden setting changes, WRITE_SECURE_SETTINGS or banking app modification is used. **Fully automatic off-and-on switching is not possible with ordinary Android permissions.**

## Quick setup

1. Install the APK, open Meowto Ranger.
2. Tap **เปิดสิทธิ์ Accessibility** and enable Meowto Ranger in Android Accessibility Settings. Sideloaded APKs may require the user to allow restricted settings.
3. Return to Meowto Ranger and select **เปิด LINE Rangers**. Confirm sending LINE messages.
4. In LINE Rangers, press the floating **▶** to begin; press **■** to stop.
5. When leaving the game, wait for the service to turn itself off. Check Accessibility Settings if K PLUS still warns.
6. For a guaranteed intentional shutdown before opening K PLUS, tap **ปิด Accessibility ทันที (ก่อนใช้ K PLUS)** in Meowto Ranger.
7. The next game session requires manually enabling Accessibility again.

## Safety and limitations

- Input and screenshots are limited to the LINE Rangers foreground app.
- A daily limit of 50 friend messages is enforced through the game counter.
- No K PLUS content is captured, tapped or controlled.
- Builds in GitHub Actions are **preview/debug** builds and are not tested against a real phone, LINE Rangers or K PLUS.
- Preview/debug and future release-signed APKs may require uninstall/reinstall when signing certificates differ.
- In-app updates use official GitHub Releases with package/version/hash checks. No signed release has been verified for v0.10.

## Project files

- `app/src/main/java/com/itonkung/meowtoranger/FriendAccessibilityService.kt`: Accessibility game engine, safe automatic shutdown.
- `app/src/main/java/com/itonkung/meowtoranger/MainActivity.kt`: status, consent, game launcher, shutdown and settings shortcuts.
- `app/src/main/res/xml/friend_accessibility_config.xml`: Android service declaration.
- `config/friend-points.json`: editable validated timing controls.
- `docs/RELEASING.md`: stable signing and update process.
