"use client";

import { db } from "../../lib/firebase";

import {
  ref,
  set,
} from "firebase/database";

export default function HostPage() {

  const testFirebase = async () => {

    await set(ref(db, "game"), {
      question: "HELLO WORLD",
      answer: "TEST ANSWER",
      showAnswer: true,
    });

    alert("WROTE TO FIREBASE");

  };

  return (

    <div className="min-h-screen bg-black flex items-center justify-center">

      <button
        onClick={testFirebase}
        className="bg-yellow-400 text-black text-5xl font-black px-20 py-10 rounded-3xl"
      >
        TEST FIREBASE
      </button>

    </div>

  );

}