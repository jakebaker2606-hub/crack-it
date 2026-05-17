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

  const [timerInput, setTimerInput] = useState(30);

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

      <h1 className="text-7xl font-black text-center text-yellow-400 mb-10">
        HOST CONTROL PANEL
      </h1>

      {/* QUESTION */}

      <div className="grid gap-5 mb-10">

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
              showAnswer: false,
            })
          }
          className="bg-yellow-500 text-black p-6 rounded-2xl text-3xl font-black"
        >
          SHOW QUESTION
        </button>

        <button
          onClick={() =>
            updateGame({
              question: "",
            })
          }
          className="bg-yellow-800 p-6 rounded-2xl text-3xl font-black"
        >
          REMOVE QUESTION
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

        <button
          onClick={() =>
            updateGame({
              answer: "",
              showAnswer: false,
            })
          }
          className="bg-green-900 p-6 rounded-2xl text-3xl font-black"
        >
          REMOVE ANSWER
        </button>

      </div>

      {/* TIMER */}

      <div className="grid grid-cols-3 gap-5 mb-10">

        <input
          type="number"
          value={timerInput}
          onChange={(e) =>
            setTimerInput(Number(e.target.value))
          }
          className="p-6 rounded-2xl text-black text-3xl font-bold"
        />

        <button
          onClick={() =>
            updateGame({
              timer: timerInput,
              timerRunning: true,
              showTimer: true,
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
              showTimer: false,
            })
          }
          className="bg-red-700 p-6 rounded-2xl text-3xl font-black"
        >
          STOP TIMER
        </button>

      </div>

      {/* TEAM CONTROLS */}

      <div className="grid grid-cols-2 gap-5 mb-10">

        {/* TEAM A */}

        <button
          onClick={() =>
            updateGame({
              teamA: gameState.teamA + 500,
            })
          }
          className="bg-blue-600 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM A +500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamA: Math.max(0, gameState.teamA - 500),
            })
          }
          className="bg-blue-900 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM A -500
        </button>

        {/* TEAM B */}

        <button
          onClick={() =>
            updateGame({
              teamB: gameState.teamB + 500,
            })
          }
          className="bg-pink-600 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM B +500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamB: Math.max(0, gameState.teamB - 500),
            })
          }
          className="bg-pink-900 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM B -500
        </button>

        {/* TEAM C */}

        <button
          onClick={() =>
            updateGame({
              teamC: gameState.teamC + 500,
            })
          }
          className="bg-green-600 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM C +500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamC: Math.max(0, gameState.teamC - 500),
            })
          }
          className="bg-green-900 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM C -500
        </button>

      </div>

      {/* GAME CONTROLS */}

      <div className="grid grid-cols-2 gap-5">

        {/* CHAOS ROUND */}

        <button
          onClick={() =>
            updateGame({
              showChaosWheel: true,
              chaosResult: "",
            })
          }
          className="bg-yellow-500 text-black p-8 rounded-3xl text-3xl font-black"
        >
          START CHAOS ROUND
        </button>

        <button
          onClick={() =>
            updateGame({
              showChaosWheel: false,
              chaosResult: "",
            })
          }
          className="bg-yellow-800 p-8 rounded-3xl text-3xl font-black"
        >
          CLOSE CHAOS ROUND
        </button>

        {/* INTRO */}

        <button
          onClick={() =>
            updateGame({
              showIntro: true,
            })
          }
          className="bg-purple-600 p-8 rounded-3xl text-3xl font-black"
        >
          START INTRO
        </button>

        <button
          onClick={() =>
            updateGame({
              showIntro: false,
            })
          }
          className="bg-purple-900 p-8 rounded-3xl text-3xl font-black"
        >
          STOP INTRO
        </button>

        {/* LEADERBOARD */}

        <button
          onClick={() =>
            updateGame({
              showLeaderboard: true,
            })
          }
          className="bg-orange-500 p-8 rounded-3xl text-3xl font-black"
        >
          SHOW LEADERBOARD
        </button>

        <button
          onClick={() =>
            updateGame({
              showLeaderboard: false,
            })
          }
          className="bg-orange-900 p-8 rounded-3xl text-3xl font-black"
        >
          HIDE LEADERBOARD
        </button>

        {/* WINNER */}

        <button
          onClick={() => {

            const scores = [
              {
                name: "TEAM A",
                score: gameState.teamA,
              },
              {
                name: "TEAM B",
                score: gameState.teamB,
              },
              {
                name: "TEAM C",
                score: gameState.teamC,
              },
            ];

            const winner = scores.reduce((a, b) =>
              a.score > b.score ? a : b
            );

            updateGame({
              winner: winner.name,
            });

          }}
          className="bg-green-500 p-8 rounded-3xl text-3xl font-black"
        >
          REVEAL WINNER
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "",
            })
          }
          className="bg-green-900 p-8 rounded-3xl text-3xl font-black"
        >
          REMOVE WINNER
        </button>

        {/* END GAME */}

        <button
          onClick={() =>
            updateGame({
              endGame: true,
            })
          }
          className="bg-black border-4 border-red-600 p-8 rounded-3xl text-3xl font-black"
        >
          END GAME
        </button>

        <button
          onClick={() =>
            updateGame({
              endGame: false,
            })
          }
          className="bg-gray-700 p-8 rounded-3xl text-3xl font-black"
        >
          REMOVE END GAME
        </button>

        {/* RESET */}

        <button
          onClick={() =>
            updateGame({

              teamA: 0,
              teamB: 0,
              teamC: 0,

              question: "",
              answer: "",

              showAnswer: false,

              timer: 30,
              timerRunning: false,
              showTimer: false,

              winner: "",

              showLeaderboard: false,

              showChaosWheel: false,
              chaosResult: "",

              showIntro: false,

              endGame: false,

            })
          }
          className="bg-red-800 p-8 rounded-3xl text-3xl font-black col-span-2"
        >
          RESET ENTIRE GAME
        </button>

      </div>

    </div>

  );

}