"use client";

import { useEffect, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
} from "firebase/database";

export default function AudiencePage() {

  const [game, setGame] = useState<any>({});

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, (snapshot) => {

      console.log(snapshot.val());

      setGame(snapshot.val());

    });

    return () => unsubscribe();

  }, []);

  return (

    <div className="min-h-screen bg-black text-white flex flex-col items-center justify-center gap-10">

      <div className="text-7xl font-black text-yellow-400">
        {game?.question || "NO QUESTION"}
      </div>

      <div className="text-5xl">
        {game?.answer || "NO ANSWER"}
      </div>

    </div>

  );

}