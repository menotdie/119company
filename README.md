# 119company

ride.setla.co.kr 라이더 실적(완료·거절·취소 등)을 보여주는 **비공식** 안드로이드 뷰어 앱. 해당 서비스와 무관한 개인 제작물이다.

## 빌드 환경

- JDK 17 (Java/Kotlin 타깃 17)
- Android SDK: compileSdk / targetSdk 35, minSdk 26
- Gradle 8.13 (wrapper 포함), AGP 8.7.3, Kotlin 2.0.21

프로젝트 루트에 `local.properties` 를 만들고 SDK 경로를 지정한다 (커밋 금지, gitignore 됨):

```properties
sdk.dir=/path/to/Android/Sdk
```

## 빌드

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/119company_v1.1.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/119company_v1.1.apk
```

- release 는 `~/.android/locode-signing.properties` 가 있으면 그 키로, 없으면 debug 키로 서명된다. 파일 형식:

  ```properties
  storeFile=/절대경로/keystore.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```

- release 빌드 후 `publishGithubRelease` 태스크가 `gh release create` 를 시도한다. 권한이 없으면 경고만 남기고 빌드는 성공한다.

## UDP 디버그 로그 (선택)

`local.properties` 에 아래 키를 넣으면 앱 로그를 UDP 로 전송한다. 없으면 로거는 꺼진다.

```properties
# 여러 대상은 쉼표로 구분
udpLog.hosts=192.0.2.10,192.0.2.11
udpLog.port=4567
```
