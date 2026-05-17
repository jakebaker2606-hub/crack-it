"use client";

import { useEffect, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
} from "firebase/database";

export default function AudiencePage() {

  const [game, setGame] = useState<any>({
    question: "",
    answer: "",
    showAnswer: false,
  });

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGame(data);
      }

    });

    return () => unsubscribe();

  }, []);

  return (

    <div className="min-h-screen bg-black text-white flex flex-col items-center justify-center p-10">

      <h1 className="text-yellow-400 text-8xl font-black mb-10">
        CRACK IT!
      </h1>

      <div className="bg-yellow-400 text-black text-5xl font-black p-10 rounded-3xl text-center max-w-5xl">
        {game.question || "Waiting for question..."}
      </div>

      {game.showAnswer && (

        <div className="bg-green-500 text-white text-5xl font-black p-10 rounded-3xl text-center max-w-5xl mt-10">
          ANSWER: {game.answer}
        </div>

      )}

    </div>

  );

}