const { onDocumentCreated, onDocumentUpdated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const db = getFirestore();
const messaging = getMessaging();

/**
 * Fetch all valid FCM tokens for a given user UID from notificationTokens subcollection
 * and fallback to user root document.
 */
async function getUserTokens(uid) {
  if (!uid) return [];
  const tokens = new Set();

  try {
    const tokensSnap = await db.collection("users").doc(uid).collection("notificationTokens").get();
    tokensSnap.forEach((doc) => {
      const token = doc.data().token || doc.id;
      if (token && typeof token === "string" && token.length > 10) {
        tokens.add(token);
      }
    });
  } catch (err) {
    console.warn(`Error reading notificationTokens for ${uid}:`, err);
  }

  if (tokens.size === 0) {
    try {
      const userDoc = await db.collection("users").doc(uid).get();
      if (userDoc.exists) {
        const data = userDoc.data();
        const mainToken = data.fcmToken || data.fcm_token;
        if (mainToken && typeof mainToken === "string" && mainToken.length > 10) {
          tokens.add(mainToken);
        }
      }
    } catch (err) {
      console.warn(`Error reading root fcmToken for ${uid}:`, err);
    }
  }

  return Array.from(tokens);
}

/**
 * Remove invalid or unregistered FCM tokens from Firestore subcollection.
 */
async function cleanupInvalidTokens(uid, invalidTokens) {
  if (!uid || !invalidTokens || invalidTokens.length === 0) return;
  const batch = db.batch();
  for (const token of invalidTokens) {
    const ref = db.collection("users").doc(uid).collection("notificationTokens").doc(token);
    batch.delete(ref);
  }
  try {
    await batch.commit();
    console.log(`Cleaned up ${invalidTokens.length} stale FCM tokens for user ${uid}`);
  } catch (err) {
    console.warn(`Failed to cleanup invalid tokens for ${uid}:`, err);
  }
}

/**
 * Helper to send FCM multicast notifications safely to all user devices.
 */
async function sendNotificationToUser(receiverUid, dataPayload, notificationTitle, notificationBody) {
  const tokens = await getUserTokens(receiverUid);
  if (tokens.length === 0) {
    console.log(`No FCM tokens found for user ${receiverUid}, skipping push.`);
    return;
  }

  // Ensure all values in dataPayload are string values
  const stringData = {};
  for (const [k, v] of Object.entries(dataPayload)) {
    if (v !== undefined && v !== null) {
      stringData[k] = String(v);
    }
  }

  const message = {
    tokens: tokens,
    data: stringData,
    notification: {
      title: notificationTitle,
      body: notificationBody,
    },
    android: {
      priority: "high",
      notification: {
        channelId: stringData.type === "call" ? "incoming_call_channel" : "plenxo_messages",
        priority: "high",
        defaultSound: true,
        icon: "ic_stat_plenxo_notification",
      },
    },
  };

  try {
    const response = await messaging.sendEachForMulticast(message);
    console.log(`FCM Multicast sent to ${receiverUid}: ${response.successCount} success, ${response.failureCount} failed`);

    const invalidTokens = [];
    response.responses.forEach((resp, idx) => {
      if (!resp.success && resp.error) {
        const code = resp.error.code;
        if (
          code === "messaging/invalid-registration-token" ||
          code === "messaging/registration-token-not-registered"
        ) {
          invalidTokens.push(tokens[idx]);
        }
      }
    });

    if (invalidTokens.length > 0) {
      await cleanupInvalidTokens(receiverUid, invalidTokens);
    }
  } catch (err) {
    console.error(`Error sending FCM multicast to ${receiverUid}:`, err);
  }
}

/**
 * 1. Firestore Trigger: New Chat Message
 */
exports.onMessageCreated = onDocumentCreated("messages/{messageId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;
  const data = snapshot.data();
  if (!data) return;

  const receiverId = data.receiverId || data.receiverUid || data.recipientId;
  const senderId = data.senderId || data.senderUid || data.from;
  if (!receiverId || !senderId || receiverId === senderId) return;

  let senderName = "Plenxo User";
  let avatarUrl = "";
  try {
    const senderDoc = await db.collection("users").doc(senderId).get();
    if (senderDoc.exists) {
      const sData = senderDoc.data();
      senderName = sData.displayName || sData.name || senderName;
      avatarUrl = sData.profilePicUrl || sData.photoUrl || "";
    }
  } catch (err) {
    console.warn("Failed to fetch sender profile for push:", err);
  }

  let bodyText = data.messageText || "";
  const msgType = (data.messageType || "TEXT").toUpperCase();
  if (msgType === "IMAGE") bodyText = "📷 Photo";
  else if (msgType === "VIDEO") bodyText = "📹 Video";
  else if (msgType === "VOICE" || msgType === "AUDIO") bodyText = "🎤 Voice Message";
  else if (msgType === "FILE") bodyText = "📁 File Attachment";

  const dataPayload = {
    type: "chat",
    chat_id: data.chatId || "",
    chatId: data.chatId || "",
    sender_id: senderId,
    senderId: senderId,
    sender_name: senderName,
    senderName: senderName,
    message_text: bodyText,
    message: bodyText,
    avatar_url: avatarUrl,
    message_id: event.params.messageId,
    timestamp: data.timestamp || Date.now(),
  };

  await sendNotificationToUser(receiverId, dataPayload, senderName, bodyText);
});

/**
 * 2. Firestore Trigger: New Friend Request
 */
exports.onFriendRequestCreated = onDocumentCreated("friend_requests/{requestId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;
  const data = snapshot.data();
  if (!data) return;

  const receiverId = data.requestTo || data.receiverUid || data.receiverId;
  const senderId = data.requestFrom || data.senderUid || data.senderId;
  if (!receiverId || !senderId) return;

  const senderName = data.senderName || "Plenxo User";
  const title = "New Friend Request";
  const body = `${senderName} sent you a friend request`;

  const dataPayload = {
    type: "friend_request",
    requestId: event.params.requestId,
    request_id: event.params.requestId,
    senderId: senderId,
    sender_id: senderId,
    senderName: senderName,
    sender_name: senderName,
    senderPhotoUrl: data.senderProfilePic || "",
    timestamp: data.timestamp || Date.now(),
  };

  await sendNotificationToUser(receiverId, dataPayload, title, body);
});

/**
 * 3. Firestore Trigger: Friend Request Acceptance
 */
exports.onFriendRequestUpdated = onDocumentUpdated("friend_requests/{requestId}", async (event) => {
  const beforeData = event.data.before.data();
  const afterData = event.data.after.data();
  if (!beforeData || !afterData) return;

  if (beforeData.status !== "ACCEPTED" && afterData.status === "ACCEPTED") {
    const receiverId = afterData.requestFrom || afterData.senderUid || afterData.senderId;
    const acceptorId = afterData.requestTo || afterData.receiverUid || afterData.receiverId;
    if (!receiverId || !acceptorId) return;

    let acceptorName = "Your friend";
    try {
      const userDoc = await db.collection("users").doc(acceptorId).get();
      if (userDoc.exists) {
        acceptorName = userDoc.data().displayName || acceptorName;
      }
    } catch (e) {
      console.warn("Failed to fetch acceptor name:", e);
    }

    const title = "Friend Request Accepted";
    const body = `${acceptorName} accepted your friend request`;

    const dataPayload = {
      type: "friend_request_accepted",
      requestId: event.params.requestId,
      senderId: acceptorId,
      senderName: acceptorName,
      timestamp: Date.now(),
    };

    await sendNotificationToUser(receiverId, dataPayload, title, body);
  }
});

/**
 * 4. Firestore Trigger: WebRTC Call Offer
 */
exports.onCallCreated = onDocumentCreated("calls/{callId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;
  const data = snapshot.data();
  if (!data) return;

  const receiverUid = data.receiverUid || data.peerUid;
  const callerUid = data.callerUid || data.senderUid;
  if (!receiverUid || !callerUid) return;

  const callerName = data.callerName || "Plenxo User";
  const callType = data.callType || "VOICE";

  const dataPayload = {
    type: "call",
    callId: event.params.callId,
    callerUid: callerUid,
    callerName: callerName,
    callerAvatar: data.callerAvatar || "",
    plenxoId: data.plenxoId || "",
    callType: callType,
    timestamp: data.createdAt || Date.now(),
  };

  const title = `Incoming ${callType.toLowerCase()} call`;
  const body = `${callerName} is calling you...`;

  await sendNotificationToUser(receiverUid, dataPayload, title, body);
});

/**
 * 5. Firestore Trigger: User Profile Written (Maintains /user_lookup/{plenxoId} exact index)
 */
exports.onUserProfileWritten = onDocumentWritten("users/{userId}", async (event) => {
  const uid = event.params.userId;
  const beforeData = event.data?.before?.data();
  const afterData = event.data?.after?.data();

  // If deleted, remove user_lookup entry
  if (!afterData) {
    if (beforeData && beforeData.plenxoId) {
      const oldPid = String(beforeData.plenxoId).trim();
      if (oldPid) {
        await db.collection("user_lookup").doc(oldPid).delete();
      }
    }
    return;
  }

  const pId = String(afterData.plenxoId || afterData.userCode || "").trim();
  if (!pId) return;

  const formattedPxId = pId.startsWith("PX-") ? pId : (pId.length === 6 && /^\d+$/.test(pId) ? `PX-${pId}` : pId);

  // If Plenxo ID changed, clean up old lookup entry
  if (beforeData && beforeData.plenxoId && beforeData.plenxoId !== formattedPxId) {
    const oldPid = String(beforeData.plenxoId).trim();
    if (oldPid) {
      await db.collection("user_lookup").doc(oldPid).delete();
    }
  }

  // Create or update minimal, privacy-safe lookup document
  const safeLookup = {
    plenxoId: formattedPxId,
    uid: uid,
    displayName: afterData.displayName || afterData.name || "Plenxo User",
    profilePicUrl: afterData.profilePicUrl || afterData.photoUrl || "",
    bio: afterData.bio || afterData.statusMessage || "",
    profileRingId: afterData.profileRingId || afterData.selectedRingId || "none",
    updatedAt: Date.now()
  };

  await db.collection("user_lookup").doc(formattedPxId).set(safeLookup, { merge: true });
});
