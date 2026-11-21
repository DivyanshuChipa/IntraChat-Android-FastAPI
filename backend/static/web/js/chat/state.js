export const state = {
  myUsername: localStorage.getItem("username"),
  ws: null,
  currentReceiver: "Family Group",
  userAvatars: {},
  unreadCounts: {},
  allUsersList: [],
  latestMessageTimes: {},
  lastRenderedDateStr: null,
  lastTypingSent: 0,
  // WebRTC call state
  peerConnection: null,
  localStream: null,
  callReceiver: null,
  isCaller: false,
  pendingOffer: null
};
