// app/host/page.tsx

"use client";

import { useEffect, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  update,
  onValue,
} from "firebase/database";

import { playSound, stopSound } from "../../lib/sounds";

export default function HostPage() {

  const [gameState, setGameState] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,

    round: 1,

    question: "Waiting for next question...",

    winner: "",

    showChaosWheel: false,

    chaosResult: "",
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

  const addTeamA = async () => {

    playSound("correct.mp3");

    await updateGame({
      teamA: gameState.teamA + 500,
    });

  };

  const addTeamB = async () => {

    playSound("correct.mp3");

    await updateGame({
      teamB: gameState.teamB + 500,
    });

  };

  const addTeamC = async () => {

    playSound("correct.mp3");

    await updateGame({
      teamC: gameState.teamC + 500,
    });

  };

  const minusTeamA = async () => {

    playSound("wrong.mp3");

    await updateGame({
      teamA: Math.max(0, gameState.teamA - 500),
    });

  };

  const minusTeamB = async () => {

    playSound("wrong.mp3");

    await updateGame({
      teamB: Math.max(0, gameState.teamB - 500),
    });

  };

  const minusTeamC = async () => {

    playSound("wrong.mp3");

    await updateGame({
      teamC: Math.max(0, gameState.teamC - 500),
    });

  };

  const openChaosRound = async () => {

    await updateGame({
      showChaosWheel: true,
      chaosResult: "",
    });

  };

  const closeChaosRound = async () => {

    await updateGame({
      showChaosWheel: false,
      chaosResult: "",
    });

  };

  return (

    <div className="min-h-screen bg-black text-white p-10">

      <h1 className="text-7xl font-black text-center text-yellow-400 mb-10">
        HOST CONTROL PANEL
      </h1>

      <div className="grid grid-cols-2 gap-8">

        <button
          onClick={addTeamA}
          className="bg-blue-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM A +500
        </button>

        <button
          onClick={minusTeamA}
          className="bg-red-700 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM A -500
        </button>

        <button
          onClick={addTeamB}
          className="bg-pink-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM B +500
        </button>

        <button
          onClick={minusTeamB}
          className="bg-red-700 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM B -500
        </button>

        <button
          onClick={addTeamC}
          className="bg-green-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM C +500
        </button>

        <button
          onClick={minusTeamC}
          className="bg-red-700 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM C -500
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM A",
            })
          }
          className="bg-blue-800 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM A WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM B",
            })
          }
          className="bg-pink-800 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM B WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM C",
            })
          }
          className="bg-green-800 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM C WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "",
            })
          }
          className="bg-gray-700 p-10 rounded-3xl text-3xl font-black"
        >
          CLEAR WINNER
        </button>

        <button
          onClick={openChaosRound}
          className="bg-yellow-500 text-black p-10 rounded-3xl text-3xl font-black"
        >
          OPEN CHAOS ROUND
        </button>

        <button
          onClick={closeChaosRound}
          className="bg-gray-700 p-10 rounded-3xl text-3xl font-black"
        >
          CLOSE CHAOS ROUND
        </button>

        <button
          onClick={() => {

            stopSound();

            updateGame({
              timer: 0,
            });

          }}
          className="bg-red-900 p-10 rounded-3xl text-3xl font-black"
        >
          STOP TIMER
        </button>

      </div>

    </div>

  );

}