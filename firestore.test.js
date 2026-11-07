const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create and read own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Alice Test",
      email: "alice@test.com",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(profileRef.get());
});

test("Authenticated user: cannot read another user's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Test",
      email: "alice@test.com",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Liked songs: owner can add and list liked songs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const songRef = aliceDb.collection("users").doc(ALICE_UID).collection("liked_songs").doc("track_01");

  await assertSucceeds(
    songRef.set({
      id: "track_01",
      userId: ALICE_UID,
      title: "Shape of You",
      artist: "Ed Sheeran",
      thumbnailUrl: "https://example.com/thumb.jpg",
      duration: "3:53",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("liked_songs").get());
});

test("Liked songs: cross-user read/write is rejected", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceSongRef = bobDb.collection("users").doc(ALICE_UID).collection("liked_songs").doc("track_01");

  await assertFails(
    aliceSongRef.set({
      id: "track_01",
      userId: ALICE_UID,
      title: "Shape of You",
      artist: "Ed Sheeran",
      createdAt: new Date(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("liked_songs").get());
});
