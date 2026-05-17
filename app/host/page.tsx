"use client";

import { useEffect, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  update,
  onValue,
} from "firebase/database";

export default function HostPage() {

  const [question, setQuestion] = useState("");

  const [answer, setAnswer] = useState("");

  const [timer, setTimerValue] = useState(30);

  const [gameState, setGameState] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,
  });

  useEffect(() => {

    const gameRef = ref(db, "game");

    onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGameState(data);
      }

    });

  }, []);

  const updateGame = async (updates: any) => {

    await update(ref(db, "game"), updates);

  };

  return (

    <div className="min-h-screen bg-black text-white p-10">

      <h1 className="text-7xl font-black text-yellow-400 text-center mb-10">
        HOST CONTROL PANEL
      </h1>

      {/* QUESTION */}

      <div className="grid gap-6 mb-10">

        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Enter Question"
          className="p-6 rounded-2xl text-black text-3xl font-bold"
        />

        <button
          onClick={() =>
            updateGame({
              question,
            })
          }
          className="bg-yellow-500 text-black p-6 rounded-2xl text-3xl font-black"
        >
          SHOW QUESTION
        </button>

        <input
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="Enter Answer"
          className="p-6 rounded-2xl text-black text-3xl font-bold"
        />

        <button
          onClick={() =>
            updateGame({
              answer,
              showAnswer: true,
            })
          }
          className="bg-green-600 p-6 rounded-2xl text-3xl font-black"
        >
          SHOW ANSWER
        </button>

      </div>

      {/* TIMER */}

      <div className="grid grid-cols-3 gap-6 mb-10">

        <input
          type="number"
          value={timer}
          onChange={(e) =>
            setTimerValue(Number(e.target.value))
          }
          className="p-6 rounded-2xl text-black text-3xl font-bold"
        />

        <button
          onClick={() =>
            updateGame({
              timer,
              timerRunning: true,
            })
          }
          className="bg-blue-600 p-6 rounded-2xl text-3xl font-black"
        >
          START TIMER
        </button>

        <button
          onClick={() =>
            updateGame({
              timerRunning: false,
            })
          }
          className="bg-red-700 p-6 rounded-2xl text-3xl font-black"
        >
          STOP TIMER
        </button>

      </div>

      {/* TEAM BUTTONS */}

      <div className="grid grid-cols-3 gap-6">

        <button
          onClick={() =>
            updateGame({
              teamA: gameState.teamA + 500,
            })
          }
          className="bg-blue-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM A +500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamB: gameState.teamB + 500,
            })
          }
          className="bg-pink-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM B +500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamC: gameState.teamC + 500,
            })
          }
          className="bg-green-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM C +500
        </button>

      </div>

      {/* GAME CONTROLS */}

      <div className="grid grid-cols-2 gap-6 mt-10">

        <button
          onClick={() =>
            updateGame({
              showChaosWheel: true,
              chaosResult: "",
            })
          }
          className="bg-yellow-500 text-black p-8 rounded-3xl text-3xl font-black"
        >
          OPEN CHAOS ROUND
        </button>

        <button
          onClick={() =>
            updateGame({
              teamA: 0,
              teamB: 0,
              teamC: 0,
              question: "Waiting for next question...",
              answer: "",
              showAnswer: false,
              winner: "",
            })
          }
          className="bg-red-800 p-8 rounded-3xl text-3xl font-black"
        >
          RESET GAME
        </button>

      </div>

    </div>

  );

}