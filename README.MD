# 🚀 StarTrack AR

**StarTrack AR** is an Android-based **Markerless Augmented Reality (AR)** application that enables users to visualize and track **stars, planets, satellites, the International Space Station (ISS), and other celestial objects** in real time. By combining GPS, CameraX, device sensors, and live astronomical APIs, the application accurately projects celestial objects onto the smartphone camera, providing an immersive astronomy and space exploration experience.

---

## ✨ Features

- 🛰️ Real-time International Space Station (ISS) tracking
- 🌌 Markerless AR visualization of stars, planets, and satellites
- 📍 GPS-based user location tracking
- 🧭 Real-time device orientation using accelerometer and magnetometer
- 📷 Live camera overlay using CameraX
- ☁️ Weather integration for sky observation conditions
- 🤖 AI-powered astronomy chatbot
- 📡 Live astronomical data using AstronomyAPI and N2YO API
- 🎯 Interactive AR guidance for locating celestial objects

---

## 🛠️ Tech Stack

### Development
- Java
- Android Studio
- XML

### Android Components
- CameraX
- Fused Location Provider (GPS)
- Accelerometer
- Magnetometer
- SensorManager
- OkHttp
- org.json

### APIs
- AstronomyAPI
- N2YO API
- OpenWeather API
- Gemini API

---

## 📂 Project Architecture

```
GPS + Device Sensors
        │
        ▼
 Device Orientation
        │
        ▼
AstronomyAPI / N2YO API
        │
        ▼
Celestial Coordinates
        │
        ▼
Projection Engine
        │
        ▼
 SkyOverlayView
        │
        ▼
 CameraX AR Overlay
```

---

## 📱 Modules

### 🏠 Home Dashboard
- Live ISS information
- User location
- Sky conditions
- Astronomy updates

### 📷 AR Camera
- Real-time celestial object projection
- Compass overlay
- Horizon line
- Interactive sky navigation

### 🤖 AI Chatbot
- Astronomy assistance
- Space-related queries
- Educational support

### ☁️ Weather
- Current weather
- Observation conditions

---

## 🚀 Getting Started

### Prerequisites

- Android Studio
- Android SDK 24+
- Java 8 or above
- Physical Android device (recommended)

### Installation

```bash
git clone https://github.com/Rahul-N-M/StarTrack-AR.git
```

Open the project in Android Studio and allow Gradle to sync.

---

## 🔑 API Configuration

Create or edit `SpaceApiConfig.java` and add your API keys.

```java
public class SpaceApiConfig {

    public static final String ASTRONOMY_APP_ID = "YOUR_APP_ID";

    public static final String ASTRONOMY_APP_SECRET = "YOUR_APP_SECRET";

    public static final String N2YO_API_KEY = "YOUR_N2YO_API_KEY";

    public static final String GEMINI_API_KEY = "YOUR_GEMINI_API_KEY";

    public static final String OPENWEATHER_API_KEY = "YOUR_OPENWEATHER_API_KEY";
}
```

---

## 📷 Permissions

The application requires:

- Camera
- Fine Location
- Internet
- Notification (Android 13+)

---

## 📸 Screenshots

### Home Screen

> *(Add screenshot here)*

### AR Camera View

> *(Add screenshot here)*

### AI Chatbot

> *(Add screenshot here)*

---

## 🎯 Applications

- Astronomy education
- Interactive sky exploration
- ISS tracking
- Planet and satellite visualization
- STEM learning
- Science exhibitions and demonstrations
- Amateur astronomy assistance

---

## 🔮 Future Scope

- Deep-sky object visualization
- Constellation recognition
- Telescope integration
- Meteor shower notifications
- Eclipse tracking
- Offline star catalogue
- Multi-language support

---

## 👨‍💻 Developer

**Rahul N M**

- LinkedIn: https://www.linkedin.com/in/rahul-n-m-830199288
- GitHub: https://github.com/Rahul-N-M

---

## 📄 License

This project is intended for educational and research purposes.
