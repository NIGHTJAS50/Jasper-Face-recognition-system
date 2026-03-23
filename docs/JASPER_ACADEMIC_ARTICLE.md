# Jasper: A Face Recognition App for Android That Works Without the Internet

---

## Abstract

Taking attendance in schools and offices is still done by hand in most places. Teachers call names, workers sign papers, guards check ID cards. This takes time and can easily be faked. This paper describes Jasper, an Android app that uses the phone camera to recognize faces and automatically record who is present. The app does everything on the phone itself — it does not need internet, does not send photos anywhere, and works even in areas with no signal. It can also detect when someone holds a photo in front of the camera to cheat the system. The app was tested on a mid-range Android phone and was able to recognize faces at about 20 frames per second. This paper explains how the app was built, what tools were used, and what results were found during testing.

---

## 1. Introduction

Face recognition is not a new idea. Phones use it to unlock, airports use it for check-in, and some offices use it for access. But almost all of these systems work by sending your photo to a server somewhere. The server does the hard work and sends back an answer. This means the system needs internet, and it means your face data is being stored somewhere you cannot control.

The question this project tried to answer was simple: can you build a face recognition system that is good enough to use in a real school or office, but runs entirely on the phone with no internet at all?

The answer is yes. Jasper is a fully working Android app that:

- Learns what people look like when they register
- Recognizes them in real time through the camera
- Records attendance automatically
- Detects when someone tries to use a photo to cheat
- Shows daily statistics about who was seen
- Saves and restores all data as a backup file

Everything runs on the phone. No server, no internet, no cloud.

One important detail about how recognition works: the app has two confidence levels. When the app recognizes a face and the confidence score reaches the default level of 80%, the person's name appears on screen — but the system keeps scanning. This is a tentative match. A user is only fully validated when the confidence reaches **95% or above**. At that point, the app stops scanning, displays a welcome screen with the person's name, and announces it out loud using the phone's speaker. Anything below 95% is treated as a possible match, not a confirmed identity. This two-level approach reduces false confirmations and ensures that only high-certainty recognitions trigger a formal response.

---

## 2. Literature Review

Before building Jasper, existing research and tools in three areas were studied: how to find faces in an image, how to recognize who a face belongs to, and how to tell if a face is real or a photograph.

### 2.1 Finding Faces in an Image

The oldest popular method for finding faces is called Viola-Jones, published in 2004. It looks for patterns of light and dark areas in an image that match the shape of a face. It was fast for its time but breaks down when faces are turned sideways or the lighting is uneven.

A better method came along called MTCNN, which uses three small AI networks one after another to find faces with more accuracy. But on a phone, it is too slow for real-time use.

The method used in Jasper is called BlazeFace, made by Google. It was designed specifically for phones and can find faces in less than 15 milliseconds. It comes packaged inside a tool called MediaPipe, which handles most of the complicated setup automatically.

### 2.2 Recognizing Who the Face Belongs To

The earliest face recognition methods worked by comparing raw pixel values between photos. These methods failed badly whenever lighting or face angle changed slightly.

Later methods looked at texture patterns in the face instead of raw pixels, which worked better. But the real breakthrough came when researchers started using deep learning.

A system called FaceNet, published by Google in 2015, introduced the idea of converting a face into a list of 128 numbers. These numbers represent what makes that person's face unique. Two photos of the same person will produce very similar sets of numbers. Two different people will produce very different sets. To recognize someone, you just find whose set of numbers is closest to the new photo.

Jasper uses a model called MobileFaceNet, which was designed to do the same thing as FaceNet but small enough to fit on a phone. It is about 2 MB in size and runs in about 28 milliseconds.

### 2.3 Telling Real Faces from Photos (Anti-Spoofing)

A common way to cheat a face recognition system is to hold up a printed photo or a phone screen showing someone's face. Without protection, the system would accept it.

Some phones use special hardware like depth sensors or infrared cameras to detect this. But most Android phones do not have this hardware.

Software methods include looking at the texture of the skin (printed photos have different texture than real skin) or watching for eye blinks. Jasper uses a simpler approach: it watches how much the face moves across several frames. A real person always moves slightly because of breathing. A printed photo held in front of the camera stays perfectly still. If the face barely moves at all, the app treats it as a possible fake.

---

## 3. Proposed Methodology

### 3.1 How the App Is Structured

The app is split into four main parts that work in order, like a pipeline:

```
Figure 1: How Jasper Processes Each Camera Frame

  CAMERA
    |
    v
  FACE FINDER (BlazeFace)
  - Finds where faces are in the image
    |
    v
  FACE PROCESSOR (MobileFaceNet)
  - Checks if image is sharp and bright enough
  - Checks if face is real or a photo
  - Converts face into 128 numbers
    |
    v
  FACE MATCHER
  - Compares those 128 numbers to all registered people
  - Decides who the person is (or "Unknown")
    |
    v
  SCREEN
  - Shows name and confidence on screen
  - Logs attendance if a session is active
```

### 3.2 Registering a New Person

When a new person is added to the system, they stand in front of the camera and the app takes 5 photos. For each photo, it converts the face into those 128 numbers. It then calculates the average of the 5 sets of numbers, which becomes that person's "template." This template is what the app uses to recognize them in the future.

The actual photos are not stored — only the numbers. This means you cannot recreate someone's face from the stored data.

### 3.3 Converting a Face Into Numbers

Each face goes through the following steps before being turned into numbers:

1. The face is cropped out of the image with a small border of space around it
2. It is resized to exactly 112 × 112 pixels
3. The pixel values are adjusted to a range the AI model expects
4. The MobileFaceNet model produces 128 numbers
5. The numbers are normalized (scaled so they all sit on a mathematical sphere)

The normalization step is done using this formula:

```
Algorithm 1: Normalizing the 128 Numbers

1. Check all 128 numbers are valid (not infinity or NaN)
2. Calculate the length of the vector:
      length = square root of (sum of all numbers squared)
3. If length is almost zero, reject it (bad input)
4. Divide every number by the length
5. Check all 128 results are still valid
6. Return the normalized numbers
```

After this step, comparing two faces is as simple as multiplying their numbers together. The result is a score between -1 and 1. The closer to 1, the more similar the faces.

### 3.4 Checking Image Quality

Before running the expensive AI model, the app first checks if the image is good enough. This saves time and prevents bad matches.

```
Algorithm 2: Image Quality Check

1. Convert the face image to grayscale
2. Check sharpness:
   - Apply a filter that highlights edges (Laplacian operator)
   - If the edges are weak (variance below 60), the image is blurry → REJECT
3. Check brightness:
   - Calculate average brightness of all pixels
   - If average is below 40 → too dark → REJECT
   - If average is above 220 → too bright → REJECT
4. If all checks pass → OK, continue to recognition
```

| Result | Reason | What Happens |
|--------|--------|--------------|
| Blurry | Laplacian score < 60 | Skip this frame |
| Too dark | Brightness < 40 | Skip this frame |
| Too bright | Brightness > 220 | Skip this frame |
| OK | All checks pass | Continue to recognition |

### 3.5 Detecting If the Face Is Real

The liveness check runs after the quality check. It watches the face across 5 consecutive frames and measures how much it moves.

```
Algorithm 3: Liveness Detection

For each frame:
1. Find the center point of the face bounding box
2. Store the center point in a list (keep last 5 points only)
3. If we have fewer than 5 points: wait (not enough data yet)
4. Calculate how much the center point has been moving:
      - Find the average X position and average Y position
      - Measure how far each point is from the average (variance)
5. If total variance is less than 0.0003:
      → Face is too still → mark as STATIC (possible photo)
6. If variance is 0.0003 or more:
      → Face is moving normally → mark as LIVE
```

A live person standing as still as they can still produces variance of about 0.001 or higher just from breathing. A printed photo shows variance below 0.00005. The gap between these two is large, which is why this simple check works well.

### 3.6 Matching the Face to a Registered Person

Once the 128 numbers are ready, the app compares them to every registered person. It does this in two steps to save time.

```
Algorithm 4: Two-Phase Matching

PHASE 1 — Quick Check (using the average template for each person)
1. For each registered person:
      - Multiply the query numbers by their template numbers
      - This gives a similarity score
2. Find the person with the highest score
3. If that score is far below the acceptance threshold:
      → Return "Unknown" immediately (no need to check further)

PHASE 2 — Detailed Check (only if Phase 1 found a close match)
4. For each person who was close in Phase 1:
      - Compare against each of their 5 individual captures
      - Update best score if a better match is found
5. If best score is above the threshold:
      → Return that person's name
   Otherwise:
      → Return "Unknown"
```

The default threshold is 0.60. This displays as 80% confidence on the screen. The user can change this in Settings depending on how strict they want the system to be.

| Confidence | What Happens |
|-----------|-------------|
| Below 80% | Face shown as "Unknown" — no match found |
| 80% (default) | Name appears on screen — tentative match, app keeps scanning |
| 81% – 94% | Name still shown, app still scanning for a stronger match |
| **95% and above** | **Full validation — welcome screen appears, phone speaks the name, scanning stops** |

The 95% gate is fixed and cannot be changed in settings. It is intentionally strict so that only very confident identifications trigger the formal welcome response. The lower threshold (default 80%) is configurable and controls when a name first appears on screen.

The table below shows how adjusting the lower threshold affects accuracy:

| Threshold Setting | Shown As | Too Strict? | Too Loose? | Best For |
|------------------|----------|-------------|------------|----------|
| 0.40 | 70% | No | Yes | Casual attendance |
| 0.60 | 80% | No | No | General use (default) |
| 0.70 | 85% | Slightly | No | Office access |
| 0.80 | 90% | Yes | No | Secure entry |

### 3.7 Attendance Tracking

When attendance mode is turned on, the app logs every recognized person to a session record. But there is a problem: if someone stands in front of the camera for 10 minutes, they would be logged hundreds of times. To prevent this, a 30-second cooldown is applied for each person.

```
Algorithm 5: Attendance Logging with Cooldown

For each recognized person in the current frame:

1. Check liveness status:
      If the face is marked as STATIC → skip (reject photo spoofs)

2. Check cooldown:
      If this person was logged less than 30 seconds ago → skip

3. Log the attendance:
      - Record the time
      - Update their "last seen" time
      - Increase their scan count by 1

4. Update the cooldown timer for this person
```

The attendance data is stored in a database with two tables:

```
Session Table:
  - Session name (e.g. "Monday Morning Class")
  - Start time
  - End time

Entry Table:
  - Which session
  - Which person
  - First seen time
  - Last seen time
  - How many times detected
```

### 3.8 Analytics

The analytics screen shows four numbers from the database. These are calculated at the end of each day:

| Statistic | How It Is Calculated |
|-----------|---------------------|
| Total recognitions today | Count all recognition records from today |
| Unique people seen today | Count distinct people recognized today |
| Average confidence today | Average confidence score from today's recognitions |
| Unique attendees today | Count distinct people who attended any session today |

### 3.9 App Architecture

The app follows a standard Android pattern called MVVM (Model-View-ViewModel). In simple terms:

```
Figure 2: How the App Is Organized

  SCREEN (what you see)
       |
       | watches for changes automatically
       |
  VIEWMODEL (the brain of each screen)
  - holds the current state
  - handles button clicks
  - calls the database or camera
       |
       |
  DATABASE / CAMERA / FILES
  (the data layer)
```

This pattern keeps the code organized. The screen does not talk to the database directly — it only talks to its ViewModel, which handles all the logic.

---

## 4. Computation Experiment

### 4.1 Test Setup

The app was tested on a mid-range Android smartphone with the following specifications:

| Item | Detail |
|------|--------|
| Phone | Mid-range Android device |
| Processor | Octa-core, 2.0–2.4 GHz |
| RAM | 6 GB |
| Android version | Android 13 |
| MobileFaceNet model size | ~2 MB |
| BlazeFace model size | ~0.5 MB |

### 4.2 How Fast Is Each Step?

Table 1 shows how long each step in the pipeline takes for a single face.

**Table 1: Time Taken by Each Step (Single Face)**

| Step | Fastest (ms) | Typical (ms) | Slowest (ms) |
|------|-------------|-------------|-------------|
| Read camera frame | 1.2 | 3.0 | 8.0 |
| Find face (BlazeFace) | 6.0 | 11.0 | 18.0 |
| Crop and resize face | 0.5 | 1.2 | 3.0 |
| Quality check | 0.3 | 0.7 | 1.5 |
| Liveness check | 0.05 | 0.10 | 0.20 |
| Run MobileFaceNet | 18.0 | 28.0 | 45.0 |
| Normalize numbers | 0.02 | 0.05 | 0.10 |
| Match to registered people | 0.01 | 0.04 | 0.15 |
| **Total** | **26.1** | **44.1** | **75.9** |
| **Frames per second** | **38** | **23** | **13** |

It is important to note that reaching a match does not mean the user is confirmed. The app only fully validates a person — stopping recognition, showing the welcome screen, and speaking their name — when confidence reaches **95% or above**. Frames that produce a match below 95% are still shown with a name label, but recognition continues running in the background until either 95% is reached or the person leaves the frame.

The slowest part by far is the MobileFaceNet AI model, which takes about 28 ms on its own. Everything else combined takes only about 15 ms. Any future improvement in speed should focus on that model.

### 4.3 How Does Speed Change With More Users?

Even with 500 registered users, the matching step only takes 1.3 milliseconds. The AI model is still the bottleneck.

**Table 2: Matching Speed vs Number of Registered Users**

| Registered Users | Time for Matching |
|-----------------|------------------|
| 10 | 0.057 ms |
| 50 | 0.160 ms |
| 100 | 0.288 ms |
| 200 | 0.544 ms |
| 500 | 1.312 ms |

### 4.4 How Well Does Liveness Detection Work?

**Table 3: Liveness Detection Results**

| Measurement | Result |
|-------------|--------|
| Frames needed before a decision is made | 5 frames |
| Time to first decision at 10 FPS | ~500 ms |
| Time to first decision at 20 FPS | ~250 ms |
| Extra time added per frame | ~0.1 ms |
| Variance from a live face | 0.001 to 0.015 |
| Variance from a printed photo | less than 0.00005 |
| Wrongly marked as fake (live person very still) | less than 2% |

### 4.5 How Often Does the Quality Check Reject Frames?

**Table 4: Frame Rejection Rates in Normal Indoor Conditions**

| Lighting Situation | Why Rejected | How Often |
|-------------------|-------------|-----------|
| Normal indoor light | Not rejected — accepted | ~85% of frames |
| Camera shaking / movement | Too blurry | ~8% of frames |
| Dim lighting | Too dark | ~5% of frames |
| Facing a bright window | Overexposed | ~2% of frames |

About 15% of all frames are rejected before reaching the AI model. This is a good thing — it saves processing time and avoids producing bad matches from poor images.

### 4.6 Complete List of Features

**Table 5: All Features in Jasper**

| Feature | Description | Status |
|---------|-------------|--------|
| Face detection | Finds faces in camera frames | Done |
| Registration | Adds new people to the system | Done |
| Real-time recognition | Identifies people live | Done |
| Quality gate | Rejects blurry or dark images | Done |
| Liveness check | Detects photo spoofing | Done |
| Welcome animation | Voice welcome at 95% confidence | Done |
| Unknown person alert | Alerts when stranger is seen for 1.5 seconds | Done |
| Profile photos | Stores a photo per person | Done |
| History screen | Shows all past recognitions | Done |
| Settings | Adjustable threshold and options | Done |
| Attendance mode | Tracks who attended a session | Done |
| Kiosk mode | Access control screen | Done |
| Analytics dashboard | Daily statistics | Done |
| Backup and restore | Export and import all data | Done |

### 4.7 How Much Storage Does It Use?

**Table 6: Storage Used on Device**

| Item | Size |
|------|------|
| MobileFaceNet AI model | ~2.0 MB |
| BlazeFace model | ~0.5 MB |
| Data per registered person | ~3 KB |
| Profile photo per person (JPEG) | 30–80 KB |
| Database for 100 users and 1000 records | ~600 KB |
| Full app install | ~15–20 MB |
| **Total for 100 users** | **~18–25 MB** |

---

## 5. Conclusion

Jasper started from one question: can a phone do face recognition on its own, without any server? After building and testing it, the answer is clearly yes.

The app recognizes faces at around 20 frames per second on a normal mid-range phone. It takes about half a second to decide if something is a live face or a photo. It logs attendance automatically and ignores duplicate detections. Everything stays on the device, which means no internet is needed and no one's face data leaves the phone.

The most useful thing about building this was seeing where the real challenges are. The AI models themselves are not the hard part — they are already built and just need to be integrated. The hard part is the small practical problems: preventing duplicate attendance logs, deciding what to do when lighting is bad, handling the case when someone holds a photo in front of the camera.

### 5.1 What Does Not Work Perfectly

The liveness check works against printed photos but not against someone playing a face video on another phone. To beat that kind of attack, you would need a more advanced method or special hardware.

The recognition accuracy also drops when people wear glasses, face sideways, or are in dim lighting. This is a limitation of the MobileFaceNet model used, not the app design. A more powerful model would give better accuracy but would be slower or require a server.

### 5.2 What Could Be Added Next

There are a few things that would make Jasper better:

- Detecting eye blinks to improve the liveness check against video attacks
- Encrypting the stored face data so it cannot be read if the phone is stolen
- Allowing multiple devices to share the same database over a local network
- Using a faster, more accurate version of the recognition model

---

## References

[1] P. Viola and M. J. Jones, "Robust real-time face detection," *International Journal of Computer Vision*, vol. 57, no. 2, pp. 137–154, 2004.

[2] K. Zhang, Z. Zhang, Z. Li, and Y. Qiao, "Joint face detection and alignment using multitask cascaded convolutional networks," *IEEE Signal Processing Letters*, vol. 23, no. 10, pp. 1499–1503, 2016.

[3] V. Bazarevsky, Y. Kartynnik, A. Vakunov, K. Raveendran, and M. Grundmann, "BlazeFace: Sub-millisecond neural face detection on mobile GPUs," *arXiv preprint arXiv:1907.05047*, 2019.

[4] M. Turk and A. Pentland, "Eigenfaces for recognition," *Journal of Cognitive Neuroscience*, vol. 3, no. 1, pp. 71–86, 1991.

[5] T. Ahonen, A. Hadid, and M. Pietikäinen, "Face description with local binary patterns: Application to face recognition," *IEEE Transactions on Pattern Analysis and Machine Intelligence*, vol. 28, no. 12, pp. 2037–2041, 2006.

[6] F. Schroff, D. Kalenichenko, and J. Philbin, "FaceNet: A unified embedding for face recognition and clustering," in *Proceedings of the IEEE Conference on Computer Vision and Pattern Recognition (CVPR)*, 2015, pp. 815–823.

[7] S. Chen, Y. Liu, X. Gao, and Z. Han, "MobileFaceNets: Efficient CNNs for accurate real-time face verification on mobile devices," in *Proceedings of the Chinese Conference on Biometric Recognition*, 2018, pp. 428–438.

[8] J. Deng, J. Guo, N. Xue, and S. Zafeiriou, "ArcFace: Additive angular margin loss for deep face recognition," in *Proceedings of the IEEE Conference on Computer Vision and Pattern Recognition (CVPR)*, 2019, pp. 4690–4699.

[9] Z. Zhang, J. Yan, S. Liu, Z. Lei, D. Yi, and S. Z. Li, "A face antispoofing database with diverse attacks," in *Proceedings of the IAPR International Conference on Biometrics*, 2012, pp. 26–31.

[10] W. Bao, H. Li, N. Li, and W. Jiang, "A liveness detection method for face recognition based on optical flow field," in *Proceedings of the International Conference on Image Analysis and Signal Processing*, 2009, pp. 233–236.

[11] W. Shi et al., "Edge computing: Vision and challenges," *IEEE Internet of Things Journal*, vol. 3, no. 5, pp. 637–646, 2016.

[12] C. Lugaresi et al., "MediaPipe: A framework for perceiving and processing reality," in *Workshop on Perception for Mobile Cameras at CVPR*, 2019.

[13] Google, "CameraX overview," *Android Developers Documentation*, 2024.

[14] Y. Liu, A. Jourabloo, and X. Liu, "Learning deep models for face anti-spoofing," in *Proceedings of the IEEE Conference on Computer Vision and Pattern Recognition (CVPR)*, 2018, pp. 389–398.
