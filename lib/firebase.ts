import { initializeApp } from "firebase/app";
import { getDatabase } from "firebase/database";

const firebaseConfig = {
  apiKey: "AIzaSyB5MDvr8r_Ec7LLx1Urn11myw1MwxpF-yg",
  authDomain: "crack-it-6ee12.firebaseapp.com",
  databaseURL:
    "https://crack-it-6ee12-default-rtdb.europe-west1.firebasedatabase.app",
  projectId: "crack-it-6ee12",
  storageBucket: "crack-it-6ee12.firebasestorage.app",
  messagingSenderId: "647110560524",
  appId: "1:647110560524:web:bba9d2735cda02ff9fd729",
};

const app = initializeApp(firebaseConfig);

export const db = getDatabase(app);