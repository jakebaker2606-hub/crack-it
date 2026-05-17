"use client";

import { useEffect, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
} from "firebase/database";

export default function AudiencePage() {

  const [game, setGame] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,
    question: "",
    answer: "",
    showAnswer: false,
  });

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      console.log(data);

      if (data) {
        setGame(data);
      }

    });

    return () => unsubscribe();

  }, []);

  const formatScore = (score: number) => {

    if (score >= 1000000) {
      return `${(score / 1000000).toFixed(1)}M`;
    }

    if (score >= 1000) {
      return `${(score / 1000).toFixed(1)}K`;
    }

    return score;

  };

  return (

    <div className="min-h-screen bg-black text-white p-10">

      <h1 className="text-yellow-400 text-8xl font-black text-center">
        CRACK IT!
      </h1>

      {/* QUESTION */}

      <div className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10">
        {game.question || "Waiting for next question..."}
      </div>

      {/* ANSWER */}

      {game.showAnswer && (

        <div className="bg-green-500 text-white text-center text-5xl font-black p-10 rounded-3xl mt-6">
          ANSWER: {game.answer}
        </div>

      )}

      {/* TEAMS */}

      <div className="flex justify-center gap-10 mt-16 flex-wrap">

        <div className="bg-blue-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM A
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamA || 0)}
          </div>

        </div>

        <div className="bg-pink-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM B
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamB || 0)}
          </div>

        </div>

        <div className="bg-green-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM C
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamC || 0)}
          </div>

        </div>

      </div>

    </div>

  );

}